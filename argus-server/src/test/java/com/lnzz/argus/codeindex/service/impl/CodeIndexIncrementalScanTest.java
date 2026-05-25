package com.lnzz.argus.codeindex.service.impl;

import com.lnzz.argus.codeindex.dto.req.CodeClassPageReqDTO;
import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeClassIndexResDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.scanner.RepositoryCodeIndexDraft;
import com.lnzz.argus.codeindex.service.CodeIndexService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.codeindex.support.ScmCodeIndexFileReader;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.model.PullRequestEvent;
import com.lnzz.argus.scm.service.ScmPlatformService;
import com.lnzz.argus.scm.service.ScmPlatformServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeIndexScanServiceImpl - 增量扫描")
class CodeIndexIncrementalScanTest {

    @Mock
    private CodeIndexService codeIndexService;

    private CodeIndexScanServiceImpl service;

    @BeforeEach
    void setUp() {
        ScmCodeIndexFileReader reader = new ScmCodeIndexFileReader(new ScmPlatformServiceFactory(
                List.of(new FakeScmPlatformService(Map.of(
                        "pom.xml", rootPom(),
                        "src/main/java/com/example/NewService.java", """
                                package com.example;
                                public class NewService {}
                                """,
                        "src/main/java/com/newpkg/DemoService.java", """
                                package com.newpkg;
                                public class DemoService {}
                                """
                )))
        ));
        service = new CodeIndexScanServiceImpl(codeIndexService, reader);
    }

    @Test
    @DisplayName("新增 Java 类会合并到上一成功索引")
    void incrementalScanShouldAddNewClass() {
        prepareLatestIndex(List.of(existingClass("src/main/java/com/example/OldService.java",
                "com.example", "OldService")));
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any()))
                .thenReturn(summary(CodeIndexConstants.ScanStatus.SUCCESS));

        service.scanIncremental(scmConfig(), request(), List.of(diff("src/main/java/com/example/NewService.java", false)));

        RepositoryCodeIndexDraft draft = capturedDraft();
        assertEquals(2, draft.getClassCount());
        assertTrue(draft.getQualifiedNameToFilePath().containsKey("com.example.OldService"));
        assertTrue(draft.getQualifiedNameToFilePath().containsKey("com.example.NewService"));
    }

    @Test
    @DisplayName("修改 package 会移除同文件旧 FQN 并加入新 FQN")
    void incrementalScanShouldReplaceClassWhenPackageChanged() {
        prepareLatestIndex(List.of(existingClass("src/main/java/com/newpkg/DemoService.java",
                "com.oldpkg", "DemoService")));
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any()))
                .thenReturn(summary(CodeIndexConstants.ScanStatus.SUCCESS));

        service.scanIncremental(scmConfig(), request(), List.of(diff("src/main/java/com/newpkg/DemoService.java", false)));

        RepositoryCodeIndexDraft draft = capturedDraft();
        assertFalse(draft.getQualifiedNameToFilePath().containsKey("com.oldpkg.DemoService"));
        assertTrue(draft.getQualifiedNameToFilePath().containsKey("com.newpkg.DemoService"));
    }

    @Test
    @DisplayName("删除 Java 文件会从新索引快照中移除对应 class")
    void incrementalScanShouldRemoveDeletedClass() {
        prepareLatestIndex(List.of(
                existingClass("src/main/java/com/example/OldService.java", "com.example", "OldService"),
                existingClass("src/main/java/com/example/KeepService.java", "com.example", "KeepService")
        ));
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any()))
                .thenReturn(summary(CodeIndexConstants.ScanStatus.SUCCESS));

        service.scanIncremental(scmConfig(), request(), List.of(diff("src/main/java/com/example/OldService.java", true)));

        RepositoryCodeIndexDraft draft = capturedDraft();
        assertEquals(1, draft.getClassCount());
        assertFalse(draft.getQualifiedNameToFilePath().containsKey("com.example.OldService"));
        assertTrue(draft.getQualifiedNameToFilePath().containsKey("com.example.KeepService"));
    }

    @Test
    @DisplayName("修改 Java 文件读取失败时会移除旧 class 并保留 warning")
    void incrementalScanShouldRemoveOldClassWhenChangedFileCannotBeLoaded() {
        prepareLatestIndex(List.of(existingClass("src/main/java/com/example/MissingService.java",
                "com.example", "MissingService")));
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any()))
                .thenReturn(summary(CodeIndexConstants.ScanStatus.SUCCESS));

        service.scanIncremental(scmConfig(), request(), List.of(diff("src/main/java/com/example/MissingService.java", false)));

        RepositoryCodeIndexDraft draft = capturedDraft();
        assertEquals(0, draft.getClassCount());
        assertFalse(draft.getQualifiedNameToFilePath().containsKey("com.example.MissingService"));
        assertTrue(draft.getWarningCount() > 0);
    }

    @Test
    @DisplayName("pom.xml 变化会标记为 MODULE_RESCAN")
    void incrementalScanShouldMarkModuleRescanWhenPomChanged() {
        prepareLatestIndex(List.of(existingClass("src/main/java/com/example/OldService.java",
                "com.example", "OldService")));
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any()))
                .thenReturn(summary(CodeIndexConstants.ScanStatus.SUCCESS));

        service.scanIncremental(scmConfig(), request(), List.of(diff("pom.xml", false)));

        ArgumentCaptor<CodeIndexScanReqDTO> requestCaptor = ArgumentCaptor.forClass(CodeIndexScanReqDTO.class);
        verify(codeIndexService).saveSuccessfulIndex(eq(scmConfig()), requestCaptor.capture(), any());
        assertEquals(CodeIndexConstants.ScanType.MODULE_RESCAN, requestCaptor.getValue().getScanType());
    }

    private RepositoryCodeIndexDraft capturedDraft() {
        ArgumentCaptor<RepositoryCodeIndexDraft> captor = ArgumentCaptor.forClass(RepositoryCodeIndexDraft.class);
        verify(codeIndexService).saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), captor.capture());
        return captor.getValue();
    }

    private void prepareLatestIndex(List<CodeClassIndexResDTO> classes) {
        CodeIndexSummaryResDTO latestIndex = new CodeIndexSummaryResDTO();
        latestIndex.setIndexId(99L);
        latestIndex.setBranchName("main");
        latestIndex.setScanStatus(CodeIndexConstants.ScanStatus.SUCCESS);
        when(codeIndexService.getLatestSuccessfulIndex(1L, "main")).thenReturn(latestIndex);
        when(codeIndexService.pageClasses(eq(99L), any(CodeClassPageReqDTO.class)))
                .thenReturn(PageResult.of(classes, 1, 200, classes.size()));
    }

    private CodeIndexScanReqDTO request() {
        CodeIndexScanReqDTO requestDTO = new CodeIndexScanReqDTO();
        requestDTO.setBranchName("main");
        requestDTO.setBaseCommitSha("base123");
        requestDTO.setCommitSha("head123");
        return requestDTO;
    }

    private ScmConfig scmConfig() {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(1L);
        scmConfig.setScmProvider("gitlab");
        return scmConfig;
    }

    private DiffFile diff(String path, boolean deleted) {
        DiffFile diffFile = new DiffFile();
        diffFile.setNewPath(path);
        diffFile.setOldPath(path);
        diffFile.setDeletedFile(deleted);
        return diffFile;
    }

    private CodeClassIndexResDTO existingClass(String filePath, String packageName, String className) {
        CodeClassIndexResDTO response = new CodeClassIndexResDTO();
        response.setIndexId(99L);
        response.setModulePath("");
        response.setSourceRoot("src/main/java");
        response.setFilePath(filePath);
        response.setPackageName(packageName);
        response.setClassName(className);
        response.setQualifiedName(packageName + "." + className);
        response.setClassKind(CodeIndexConstants.ClassKind.CLASS);
        response.setPrimaryType(true);
        response.setParserStatus(CodeIndexConstants.ScanStatus.SUCCESS);
        return response;
    }

    private CodeIndexSummaryResDTO summary(String status) {
        CodeIndexSummaryResDTO response = new CodeIndexSummaryResDTO();
        response.setScanStatus(status);
        return response;
    }

    private static String rootPom() {
        return """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                </project>
                """;
    }

    private static class FakeScmPlatformService implements ScmPlatformService {

        private final Map<String, String> files;

        private FakeScmPlatformService(Map<String, String> files) {
            this.files = files;
        }

        @Override
        public String getProvider() {
            return "gitlab";
        }

        @Override
        public PullRequestEvent parseWebhookEvent(Map<String, String> headers, String payload) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean verifyWebhookSignature(ScmConfig config, Map<String, String> headers, String payload) {
            return false;
        }

        @Override
        public List<DiffFile> getPullRequestDiffs(ScmConfig config, ReviewTask task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getFileContent(ScmConfig config, ReviewTask task, String filePath, String ref) {
            return getFileContent(config, filePath, ref);
        }

        @Override
        public String getFileContent(ScmConfig config, String filePath, String ref) {
            if (!files.containsKey(filePath)) {
                throw new IllegalStateException("not found");
            }
            return files.get(filePath);
        }

        @Override
        public Long addPullRequestComment(ScmConfig config, ReviewTask task, String body) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPullRequestLabels(ScmConfig config, ReviewTask task, List<String> labels) {
            throw new UnsupportedOperationException();
        }
    }
}
