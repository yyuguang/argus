package com.lnzz.argus.codeindex.service.impl;

import com.lnzz.argus.codeindex.dao.entity.AppVersionBinding;
import com.lnzz.argus.codeindex.dao.entity.CodeClassIndex;
import com.lnzz.argus.codeindex.dao.entity.CodeRepositoryIndex;
import com.lnzz.argus.codeindex.dao.mapper.AppVersionBindingMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodeClassIndexMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodeRepositoryIndexMapper;
import com.lnzz.argus.codeindex.dto.req.SourceLocateReqDTO;
import com.lnzz.argus.codeindex.dto.res.SourceLocateResDTO;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SourceLocationServiceImpl - 索引优先源码定位")
class SourceLocationServiceImplTest {

    @Mock
    private CodeRepositoryIndexMapper repositoryIndexMapper;
    @Mock
    private CodeClassIndexMapper classIndexMapper;
    @Mock
    private AppVersionBindingMapper appVersionBindingMapper;

    private SourceLocationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SourceLocationServiceImpl(repositoryIndexMapper, classIndexMapper, appVersionBindingMapper);
    }

    @Test
    @DisplayName("精确 commit + FQN 唯一命中")
    void locateShouldMatchByCommitAndQualifiedName() {
        SourceLocateReqDTO requestDTO = request();
        CodeRepositoryIndex index = index(99L, "abc123");
        CodeClassIndex classIndex = classIndex("com.example.DemoService",
                "src/main/java/com/example/DemoService.java");
        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION))
                .thenReturn(index);
        when(classIndexMapper.selectByQualifiedName(99L, "com.example.DemoService")).thenReturn(List.of(classIndex));

        SourceLocateResDTO response = service.locate(requestDTO);

        assertTrue(response.getMatched());
        assertEquals(CodeIndexConstants.MatchType.QUALIFIED_NAME, response.getMatchType());
        assertEquals(CodeIndexConstants.Confidence.HIGH, response.getConfidence());
        assertEquals("src/main/java/com/example/DemoService.java", response.getFilePath());
        assertEquals(123, response.getLineNumber());
    }

    @Test
    @DisplayName("应用版本绑定命中对应索引")
    void locateShouldUseActiveAppVersionBinding() {
        SourceLocateReqDTO requestDTO = request();
        requestDTO.setCommitSha(null);
        requestDTO.setAppName("order-service");
        requestDTO.setEnvironment("prod");
        AppVersionBinding binding = activeBinding(200L, "deploy123");
        CodeRepositoryIndex index = index(200L, "deploy123");
        CodeClassIndex classIndex = classIndex("com.example.DemoService",
                "service/src/main/java/com/example/DemoService.java");
        when(appVersionBindingMapper.selectActiveBinding("order-service", "prod", 1L)).thenReturn(binding);
        when(repositoryIndexMapper.selectById(200L)).thenReturn(index);
        when(classIndexMapper.selectByQualifiedName(200L, "com.example.DemoService")).thenReturn(List.of(classIndex));

        SourceLocateResDTO response = service.locate(requestDTO);

        assertTrue(response.getMatched());
        assertEquals(200L, response.getIndexId());
        assertEquals("deploy123", response.getCommitSha());
        assertEquals("service/src/main/java/com/example/DemoService.java", response.getFilePath());
    }

    @Test
    @DisplayName("无 commit 和绑定时使用默认分支最新成功索引兜底")
    void locateShouldFallbackToLatestDefaultBranch() {
        SourceLocateReqDTO requestDTO = request();
        requestDTO.setCommitSha(null);
        CodeRepositoryIndex latestIndex = index(300L, "latest123");
        CodeClassIndex classIndex = classIndex("com.example.DemoService",
                "app/src/main/java/com/example/DemoService.java");
        when(repositoryIndexMapper.selectLatestSuccessful(1L, "main")).thenReturn(latestIndex);
        when(classIndexMapper.selectByQualifiedName(300L, "com.example.DemoService")).thenReturn(List.of(classIndex));

        SourceLocateResDTO response = service.locate(requestDTO);

        assertTrue(response.getMatched());
        assertEquals(300L, response.getIndexId());
        assertEquals("latest123", response.getCommitSha());
        assertEquals("app/src/main/java/com/example/DemoService.java", response.getFilePath());
    }

    @Test
    @DisplayName("简单类名多候选时不强行唯一化")
    void locateShouldReturnCandidatesWhenSimpleNameAmbiguous() {
        SourceLocateReqDTO requestDTO = request();
        requestDTO.setCommitSha(null);
        requestDTO.setQualifiedName("DemoService");
        CodeRepositoryIndex latestIndex = index(300L, "latest123");
        when(repositoryIndexMapper.selectLatestSuccessful(1L, "main")).thenReturn(latestIndex);
        when(classIndexMapper.selectByClassName(300L, "DemoService")).thenReturn(List.of(
                classIndex("com.foo.DemoService", "foo/src/main/java/com/foo/DemoService.java"),
                classIndex("com.bar.DemoService", "bar/src/main/java/com/bar/DemoService.java")
        ));

        SourceLocateResDTO response = service.locate(requestDTO);

        assertFalse(response.getMatched());
        assertEquals(CodeIndexConstants.MatchType.SIMPLE_NAME, response.getMatchType());
        assertEquals(CodeIndexConstants.Confidence.MEDIUM, response.getConfidence());
        assertEquals(2, response.getCandidates().size());
        assertTrue(response.getWarnings().stream().anyMatch(warning -> warning.contains("多个候选")));
    }

    @Test
    @DisplayName("无可用索引时返回未命中")
    void locateShouldReturnNotMatchedWhenNoIndexAvailable() {
        SourceLocateReqDTO requestDTO = request();
        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION))
                .thenReturn(null);
        when(repositoryIndexMapper.selectLatestSuccessful(1L, "main")).thenReturn(null);

        SourceLocateResDTO response = service.locate(requestDTO);

        assertFalse(response.getMatched());
        assertEquals(CodeIndexConstants.MatchType.NONE, response.getMatchType());
        assertEquals(CodeIndexConstants.Confidence.NONE, response.getConfidence());
        assertTrue(response.getWarnings().stream().anyMatch(warning -> warning.contains("未找到 commit")));
        verify(classIndexMapper, never()).selectByQualifiedName(99L, "com.example.DemoService");
    }

    private SourceLocateReqDTO request() {
        SourceLocateReqDTO requestDTO = new SourceLocateReqDTO();
        requestDTO.setScmConfigId(1L);
        requestDTO.setBranchName("main");
        requestDTO.setCommitSha("abc123");
        requestDTO.setQualifiedName("com.example.DemoService");
        requestDTO.setLineNumber(123);
        return requestDTO;
    }

    private CodeRepositoryIndex index(Long id, String commitSha) {
        CodeRepositoryIndex index = new CodeRepositoryIndex();
        index.setId(id);
        index.setScmConfigId(1L);
        index.setBranchName("main");
        index.setCommitSha(commitSha);
        index.setScanStatus(CodeIndexConstants.ScanStatus.SUCCESS);
        return index;
    }

    private AppVersionBinding activeBinding(Long indexId, String commitSha) {
        AppVersionBinding binding = new AppVersionBinding();
        binding.setAppName("order-service");
        binding.setEnvironment("prod");
        binding.setScmConfigId(1L);
        binding.setBranchName("main");
        binding.setCommitSha(commitSha);
        binding.setIndexId(indexId);
        binding.setActive(true);
        return binding;
    }

    private CodeClassIndex classIndex(String qualifiedName, String filePath) {
        CodeClassIndex classIndex = new CodeClassIndex();
        classIndex.setIndexId(99L);
        classIndex.setScmConfigId(1L);
        classIndex.setModulePath(modulePath(filePath));
        classIndex.setSourceRoot(sourceRoot(filePath));
        classIndex.setFilePath(filePath);
        classIndex.setPackageName(packageName(qualifiedName));
        classIndex.setClassName(className(qualifiedName));
        classIndex.setQualifiedName(qualifiedName);
        classIndex.setPrimaryType(true);
        classIndex.setConfidence(CodeIndexConstants.Confidence.HIGH);
        classIndex.setParserStatus(CodeIndexConstants.ScanStatus.SUCCESS);
        return classIndex;
    }

    private String packageName(String qualifiedName) {
        int dotIndex = qualifiedName.lastIndexOf('.');
        return dotIndex > 0 ? qualifiedName.substring(0, dotIndex) : "";
    }

    private String className(String qualifiedName) {
        int dotIndex = qualifiedName.lastIndexOf('.');
        return dotIndex > 0 ? qualifiedName.substring(dotIndex + 1) : qualifiedName;
    }

    private String modulePath(String filePath) {
        int sourceRootIndex = filePath.indexOf("/src/main/java");
        return sourceRootIndex > 0 ? filePath.substring(0, sourceRootIndex) : "";
    }

    private String sourceRoot(String filePath) {
        int javaIndex = filePath.indexOf("/java/");
        return javaIndex > 0 ? filePath.substring(0, javaIndex + "/java".length()) : "src/main/java";
    }
}
