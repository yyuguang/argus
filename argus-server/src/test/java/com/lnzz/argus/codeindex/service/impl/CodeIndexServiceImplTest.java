package com.lnzz.argus.codeindex.service.impl;

import com.lnzz.argus.codeindex.dao.entity.CodeClassIndex;
import com.lnzz.argus.codeindex.dao.entity.CodeModuleIndex;
import com.lnzz.argus.codeindex.dao.entity.CodePackageIndex;
import com.lnzz.argus.codeindex.dao.entity.CodeRepositoryIndex;
import com.lnzz.argus.codeindex.dao.mapper.CodeClassIndexMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodeModuleIndexMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodePackageIndexMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodeRepositoryIndexMapper;
import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.scanner.JavaFileIndex;
import com.lnzz.argus.codeindex.scanner.ModuleScanResult;
import com.lnzz.argus.codeindex.scanner.RepositoryCodeIndexDraft;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.scm.entity.ScmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeIndexServiceImpl - 源码索引持久化服务")
class CodeIndexServiceImplTest {

    @Mock
    private CodeRepositoryIndexMapper repositoryIndexMapper;
    @Mock
    private CodeModuleIndexMapper moduleIndexMapper;
    @Mock
    private CodeClassIndexMapper classIndexMapper;
    @Mock
    private CodePackageIndexMapper packageIndexMapper;

    private CodeIndexServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CodeIndexServiceImpl(repositoryIndexMapper, moduleIndexMapper, classIndexMapper, packageIndexMapper);
    }

    @Test
    @DisplayName("成功扫描结果会写入 repository/module/class/package 索引")
    void saveSuccessfulIndexShouldPersistDraft() {
        ScmConfig scmConfig = scmConfig();
        CodeIndexScanReqDTO requestDTO = scanRequest();
        RepositoryCodeIndexDraft draft = draft();

        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION)).thenReturn(null);
        when(repositoryIndexMapper.insert(any(CodeRepositoryIndex.class))).thenAnswer(invocation -> {
            CodeRepositoryIndex entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        });
        when(moduleIndexMapper.insertBatch(any())).thenReturn(1);
        when(classIndexMapper.insertBatch(any())).thenReturn(1);
        when(packageIndexMapper.insertBatch(any())).thenReturn(1);

        CodeIndexSummaryResDTO response = service.saveSuccessfulIndex(scmConfig, requestDTO, draft);

        assertEquals(100L, response.getIndexId());
        assertEquals(CodeIndexConstants.ScanStatus.SUCCESS, response.getScanStatus());
        assertEquals(1, response.getModuleCount());
        assertEquals(1, response.getClassCount());
        verify(moduleIndexMapper).insertBatch(any());
        verify(classIndexMapper).insertBatch(any());
        verify(packageIndexMapper).insertBatch(any());
    }

    @Test
    @DisplayName("相同 commit 已存在成功索引时幂等返回旧索引")
    void saveSuccessfulIndexShouldReturnExistingSuccessIndex() {
        CodeRepositoryIndex existing = existingRepositoryIndex(CodeIndexConstants.ScanStatus.SUCCESS);

        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION)).thenReturn(existing);

        CodeIndexSummaryResDTO response = service.saveSuccessfulIndex(scmConfig(), scanRequest(), draft());

        assertEquals(99L, response.getIndexId());
        assertEquals(CodeIndexConstants.ScanStatus.SUCCESS, response.getScanStatus());
        verify(repositoryIndexMapper, never()).insert(any(CodeRepositoryIndex.class));
        verify(moduleIndexMapper, never()).insertBatch(any());
        verify(classIndexMapper, never()).insertBatch(any());
        verify(packageIndexMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("强制重建会覆盖同 commit 已存在成功索引并重写明细")
    void saveSuccessfulIndexShouldRebuildExistingSuccessIndexWhenForced() {
        CodeRepositoryIndex existing = existingRepositoryIndex(CodeIndexConstants.ScanStatus.SUCCESS);
        existing.setVersion(3);
        CodeIndexScanReqDTO requestDTO = scanRequest();
        requestDTO.setForceRebuild(true);
        requestDTO.setScanType(CodeIndexConstants.ScanType.REBUILD);

        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION)).thenReturn(existing);
        when(repositoryIndexMapper.updateById(any(CodeRepositoryIndex.class))).thenReturn(1);
        when(moduleIndexMapper.deletePhysicalByIndexId(99L)).thenReturn(1);
        when(classIndexMapper.deletePhysicalByIndexId(99L)).thenReturn(1);
        when(packageIndexMapper.deletePhysicalByIndexId(99L)).thenReturn(1);
        when(moduleIndexMapper.insertBatch(any())).thenReturn(1);
        when(classIndexMapper.insertBatch(any())).thenReturn(1);
        when(packageIndexMapper.insertBatch(any())).thenReturn(1);

        CodeIndexSummaryResDTO response = service.saveSuccessfulIndex(scmConfig(), requestDTO, draft());

        assertEquals(99L, response.getIndexId());
        assertEquals(CodeIndexConstants.ScanType.REBUILD, response.getScanType());
        ArgumentCaptor<CodeRepositoryIndex> captor = ArgumentCaptor.forClass(CodeRepositoryIndex.class);
        verify(repositoryIndexMapper).updateById(captor.capture());
        assertEquals(99L, captor.getValue().getId());
        assertEquals(3, captor.getValue().getVersion());
        verify(moduleIndexMapper).deletePhysicalByIndexId(99L);
        verify(classIndexMapper).deletePhysicalByIndexId(99L);
        verify(packageIndexMapper).deletePhysicalByIndexId(99L);
        verify(moduleIndexMapper).insertBatch(any());
        verify(classIndexMapper).insertBatch(any());
        verify(packageIndexMapper).insertBatch(any());
    }

    @Test
    @DisplayName("大量类型明细会按固定批次写入，避免生成过大的单条 SQL")
    @SuppressWarnings("unchecked")
    void saveSuccessfulIndexShouldSplitClassDetailsIntoBatches() {
        ScmConfig scmConfig = scmConfig();
        CodeIndexScanReqDTO requestDTO = scanRequest();
        RepositoryCodeIndexDraft draft = largeClassDraft(1001);

        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION)).thenReturn(null);
        when(repositoryIndexMapper.insert(any(CodeRepositoryIndex.class))).thenAnswer(invocation -> {
            CodeRepositoryIndex entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        });
        when(classIndexMapper.insertBatch(any())).thenReturn(1);

        CodeIndexSummaryResDTO response = service.saveSuccessfulIndex(scmConfig, requestDTO, draft);

        assertEquals(100L, response.getIndexId());
        ArgumentCaptor<List<CodeClassIndex>> captor = ArgumentCaptor.forClass(List.class);
        verify(classIndexMapper, times(3)).insertBatch(captor.capture());
        assertEquals(500, captor.getAllValues().get(0).size());
        assertEquals(500, captor.getAllValues().get(1).size());
        assertEquals(1, captor.getAllValues().get(2).size());
        verify(moduleIndexMapper, never()).insertBatch(any());
        verify(packageIndexMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("按 commit 查询只返回成功索引")
    void getSuccessfulIndexByCommitShouldReturnOnlySuccessIndex() {
        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION))
                .thenReturn(existingRepositoryIndex(CodeIndexConstants.ScanStatus.SUCCESS))
                .thenReturn(existingRepositoryIndex(CodeIndexConstants.ScanStatus.FAILED));

        CodeIndexSummaryResDTO success = service.getSuccessfulIndexByCommit(1L, "abc123");
        CodeIndexSummaryResDTO failed = service.getSuccessfulIndexByCommit(1L, "abc123");

        assertEquals(99L, success.getIndexId());
        assertEquals(CodeIndexConstants.ScanStatus.SUCCESS, success.getScanStatus());
        assertNull(failed);
    }

    @Test
    @DisplayName("扫描失败时不覆盖同 commit 已存在成功索引，但本次结果仍返回失败")
    void markScanFailedShouldReportFailureWhenSuccessIndexExists() {
        CodeRepositoryIndex existing = existingRepositoryIndex(CodeIndexConstants.ScanStatus.SUCCESS);

        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION)).thenReturn(existing);

        CodeIndexSummaryResDTO response = service.markScanFailed(scmConfig(), scanRequest(), "SCM timeout");

        assertNull(response.getIndexId());
        assertEquals(CodeIndexConstants.ScanStatus.FAILED, response.getScanStatus());
        assertEquals("SCM timeout", response.getLatestErrorMessage());
        assertEquals("abc123", response.getCommitSha());
        verify(repositoryIndexMapper, never()).insert(any(CodeRepositoryIndex.class));
        verify(repositoryIndexMapper, never()).updateById(any(CodeRepositoryIndex.class));
    }

    @Test
    @DisplayName("扫描失败且无成功索引时只写入失败 repository 快照")
    void markScanFailedShouldPersistFailedSnapshotWhenNoSuccessIndexExists() {
        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION)).thenReturn(null);
        when(repositoryIndexMapper.insert(any(CodeRepositoryIndex.class))).thenAnswer(invocation -> {
            CodeRepositoryIndex entity = invocation.getArgument(0);
            entity.setId(101L);
            return 1;
        });

        CodeIndexSummaryResDTO response = service.markScanFailed(scmConfig(), scanRequest(), "SCM timeout");

        assertEquals(101L, response.getIndexId());
        assertEquals(CodeIndexConstants.ScanStatus.FAILED, response.getScanStatus());
        assertEquals("SCM timeout", response.getLatestErrorMessage());
        ArgumentCaptor<CodeRepositoryIndex> captor = ArgumentCaptor.forClass(CodeRepositoryIndex.class);
        verify(repositoryIndexMapper).insert(captor.capture());
        assertEquals(0, captor.getValue().getClassCount());
        verify(moduleIndexMapper, never()).insertBatch(any());
        verify(classIndexMapper, never()).insertBatch(any());
        verify(packageIndexMapper, never()).insertBatch(any());
    }

    @Test
    @DisplayName("扫描失败且请求缺少 commit 时会生成可落库的兜底提交号")
    void markScanFailedShouldFillFallbackCommitWhenCommitMissing() {
        CodeIndexScanReqDTO requestDTO = scanRequest();
        requestDTO.setCommitSha(null);
        when(repositoryIndexMapper.insert(any(CodeRepositoryIndex.class))).thenAnswer(invocation -> {
            CodeRepositoryIndex entity = invocation.getArgument(0);
            entity.setId(102L);
            return 1;
        });

        CodeIndexSummaryResDTO response = service.markScanFailed(scmConfig(), requestDTO, "未读取到可扫描文件");

        assertEquals(102L, response.getIndexId());
        assertEquals(CodeIndexConstants.ScanStatus.FAILED, response.getScanStatus());
        assertTrue(response.getCommitSha().startsWith("unresolved-main-"));
        ArgumentCaptor<CodeRepositoryIndex> captor = ArgumentCaptor.forClass(CodeRepositoryIndex.class);
        verify(repositoryIndexMapper).insert(captor.capture());
        assertTrue(captor.getValue().getCommitSha().startsWith("unresolved-main-"));
    }

    private ScmConfig scmConfig() {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(1L);
        scmConfig.setScmProvider("gitlab");
        scmConfig.setProjectId(200L);
        scmConfig.setRepoOwner("quality");
        scmConfig.setRepoName("demo-service");
        return scmConfig;
    }

    private CodeIndexScanReqDTO scanRequest() {
        CodeIndexScanReqDTO requestDTO = new CodeIndexScanReqDTO();
        requestDTO.setBranchName("main");
        requestDTO.setCommitSha("abc123");
        requestDTO.setScanType(CodeIndexConstants.ScanType.FULL);
        return requestDTO;
    }

    private RepositoryCodeIndexDraft draft() {
        ModuleScanResult module = new ModuleScanResult();
        module.setModuleName("service");
        module.setModulePath("service");
        module.setBuildType("MAVEN");
        module.setPackaging("jar");
        module.setSourceRoots(List.of("service/src/main/java"));
        module.setJavaFileCount(1);
        module.setClassCount(1);
        module.setScanStatus(CodeIndexConstants.ScanStatus.SUCCESS);

        JavaFileIndex classIndex = new JavaFileIndex();
        classIndex.setModulePath("service");
        classIndex.setSourceRoot("service/src/main/java");
        classIndex.setFilePath("service/src/main/java/com/example/DemoService.java");
        classIndex.setPackageName("com.example");
        classIndex.setClassName("DemoService");
        classIndex.setQualifiedName("com.example.DemoService");
        classIndex.setClassKind(CodeIndexConstants.ClassKind.CLASS);
        classIndex.setPrimaryType(true);
        classIndex.setParserStatus(CodeIndexConstants.ScanStatus.SUCCESS);
        classIndex.setImports(List.of("java.util.List"));

        RepositoryCodeIndexDraft.PackageDraft packageDraft = new RepositoryCodeIndexDraft.PackageDraft();
        packageDraft.setPackageName("com.example");
        packageDraft.setPrimaryModulePath("service");
        packageDraft.setModulePaths(List.of("service"));
        packageDraft.setClassCount(1);
        packageDraft.setAmbiguous(false);
        packageDraft.setConfidence(CodeIndexConstants.Confidence.HIGH);

        RepositoryCodeIndexDraft draft = new RepositoryCodeIndexDraft();
        draft.setModules(List.of(module));
        draft.setClasses(List.of(classIndex));
        draft.setPackages(List.of(packageDraft));
        draft.setModuleCount(1);
        draft.setSourceRootCount(1);
        draft.setJavaFileCount(1);
        draft.setClassCount(1);
        draft.setPackageCount(1);
        draft.setWarningCount(0);
        return draft;
    }

    private RepositoryCodeIndexDraft largeClassDraft(int classCount) {
        List<JavaFileIndex> classes = new ArrayList<>();
        for (int i = 0; i < classCount; i++) {
            JavaFileIndex classIndex = new JavaFileIndex();
            classIndex.setModulePath("service");
            classIndex.setSourceRoot("service/src/main/java");
            classIndex.setFilePath("service/src/main/java/com/example/Demo" + i + ".java");
            classIndex.setPackageName("com.example");
            classIndex.setClassName("Demo" + i);
            classIndex.setQualifiedName("com.example.Demo" + i);
            classIndex.setClassKind(CodeIndexConstants.ClassKind.CLASS);
            classIndex.setPrimaryType(true);
            classIndex.setParserStatus(CodeIndexConstants.ScanStatus.SUCCESS);
            classIndex.setImports(List.of());
            classes.add(classIndex);
        }
        RepositoryCodeIndexDraft draft = new RepositoryCodeIndexDraft();
        draft.setClasses(classes);
        draft.setModules(List.of());
        draft.setPackages(List.of());
        draft.setModuleCount(0);
        draft.setSourceRootCount(1);
        draft.setJavaFileCount(classCount);
        draft.setClassCount(classCount);
        draft.setPackageCount(0);
        draft.setWarningCount(0);
        return draft;
    }

    private CodeRepositoryIndex existingRepositoryIndex(String status) {
        CodeRepositoryIndex existing = new CodeRepositoryIndex();
        existing.setId(99L);
        existing.setScmConfigId(1L);
        existing.setBranchName("main");
        existing.setCommitSha("abc123");
        existing.setIndexVersion(CodeIndexConstants.CURRENT_INDEX_VERSION);
        existing.setScanStatus(status);
        existing.setScanType(CodeIndexConstants.ScanType.FULL);
        existing.setTriggerType(CodeIndexConstants.TriggerType.MANUAL);
        existing.setModuleCount(1);
        existing.setSourceRootCount(1);
        existing.setJavaFileCount(1);
        existing.setClassCount(1);
        existing.setPackageCount(1);
        existing.setAmbiguousPackageCount(0);
        existing.setWarningCount(0);
        existing.setConfidence(CodeIndexConstants.Confidence.HIGH);
        existing.setStale(false);
        return existing;
    }
}
