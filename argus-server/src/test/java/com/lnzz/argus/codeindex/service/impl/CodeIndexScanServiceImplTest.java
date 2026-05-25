package com.lnzz.argus.codeindex.service.impl;

import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.scanner.RepositoryCodeIndexDraft;
import com.lnzz.argus.codeindex.service.CodeIndexService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.codeindex.support.CodeIndexScanExecutionContext;
import com.lnzz.argus.codeindex.support.CodeIndexScanProgressCallback;
import com.lnzz.argus.codeindex.support.ScmCodeIndexFileReader;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeIndexScanServiceImpl - SCM 文件读取与扫描编排")
class CodeIndexScanServiceImplTest {

    @Mock
    private CodeIndexService codeIndexService;

    private CodeIndexScanServiceImpl service;

    @BeforeEach
    void setUp() {
        ScmCodeIndexFileReader reader = new ScmCodeIndexFileReader(new ScmPlatformServiceFactory(
                List.of(new FakeScmPlatformService(Map.of(
                        "pom.xml", rootPom(),
                        "src/main/java/com/example/DemoService.java", """
                                package com.example;
                                public class DemoService {}
                                """,
                        "service/pom.xml", childPom("service"),
                        "service/src/main/java/com/example/ChildService.java", """
                                package com.example;
                                public class ChildService {}
                                """
                )))
        ));
        service = new CodeIndexScanServiceImpl(codeIndexService, reader);
    }

    @Test
    @DisplayName("已知路径扫描会读取 SCM 文件、解析 Java 类型并保存索引")
    void scanKnownFilesShouldBuildAndSaveIndex() {
        CodeIndexScanReqDTO requestDTO = request(List.of(
                "pom.xml",
                "src/main/java/com/example/DemoService.java"
        ));
        CodeIndexSummaryResDTO expected = summary(CodeIndexConstants.ScanStatus.SUCCESS);
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any(RepositoryCodeIndexDraft.class)))
                .thenReturn(expected);

        CodeIndexSummaryResDTO response = service.scanKnownFiles(scmConfig(), requestDTO);

        assertEquals(CodeIndexConstants.ScanStatus.SUCCESS, response.getScanStatus());
        ArgumentCaptor<RepositoryCodeIndexDraft> draftCaptor = ArgumentCaptor.forClass(RepositoryCodeIndexDraft.class);
        verify(codeIndexService).saveSuccessfulIndex(eq(scmConfig()), eq(requestDTO), draftCaptor.capture());
        assertEquals(1, draftCaptor.getValue().getModuleCount());
        assertEquals(1, draftCaptor.getValue().getClassCount());
        assertEquals("src/main/java/com/example/DemoService.java",
                draftCaptor.getValue().getQualifiedNameToFilePath().get("com.example.DemoService"));
    }

    @Test
    @DisplayName("缺失 pom.xml 时标记扫描失败，不保存成功索引")
    void scanKnownFilesShouldFailWhenPomMissing() {
        CodeIndexScanReqDTO requestDTO = request(List.of("src/main/java/com/example/DemoService.java"));
        CodeIndexSummaryResDTO failed = summary(CodeIndexConstants.ScanStatus.FAILED);
        when(codeIndexService.markScanFailed(eq(scmConfig()), eq(requestDTO), any())).thenReturn(failed);

        CodeIndexSummaryResDTO response = service.scanKnownFiles(scmConfig(), requestDTO);

        assertEquals(CodeIndexConstants.ScanStatus.FAILED, response.getScanStatus());
        verify(codeIndexService).markScanFailed(eq(scmConfig()), eq(requestDTO), any());
        verify(codeIndexService, never()).saveSuccessfulIndex(any(), any(), any());
    }

    @Test
    @DisplayName("带回调扫描会按阶段顺序上报进度并在成功后上报索引结果")
    void scanKnownFilesShouldReportProgressStages() {
        CodeIndexScanReqDTO requestDTO = request(List.of(
                "pom.xml",
                "src/main/java/com/example/DemoService.java"
        ));
        CodeIndexSummaryResDTO expected = summary(CodeIndexConstants.ScanStatus.SUCCESS);
        expected.setIndexId(9001L);
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any(RepositoryCodeIndexDraft.class)))
                .thenReturn(expected);
        RecordingProgressCallback callback = new RecordingProgressCallback();

        CodeIndexSummaryResDTO response = service.scanKnownFiles(scmConfig(), requestDTO,
                CodeIndexScanExecutionContext.progress(1001L, callback));

        assertEquals(CodeIndexConstants.ScanStatus.SUCCESS, response.getScanStatus());
        assertEquals(List.of(
                CodeIndexConstants.ScanStage.SCM_READING,
                CodeIndexConstants.ScanStage.MODULE_SCANNING,
                CodeIndexConstants.ScanStage.SOURCE_ROOT_DISCOVERING,
                CodeIndexConstants.ScanStage.JAVA_PARSING,
                CodeIndexConstants.ScanStage.INDEX_PERSISTING
        ), callback.stageEvents());
        assertTrue(callback.events().contains("file:1001:2"));
        assertTrue(callback.events().contains("java:1001:1/1/0"));
        assertTrue(callback.events().contains("persist:1001:1/1/0"));
        assertTrue(callback.events().contains("success:1001:9001:1/1/0"));
    }

    @Test
    @DisplayName("带回调扫描失败时会上报失败原因且不保存成功索引")
    void scanKnownFilesShouldReportFailure() {
        CodeIndexScanReqDTO requestDTO = request(List.of("src/main/java/com/example/DemoService.java"));
        CodeIndexSummaryResDTO failed = summary(CodeIndexConstants.ScanStatus.FAILED);
        when(codeIndexService.markScanFailed(eq(scmConfig()), eq(requestDTO), any())).thenReturn(failed);
        RecordingProgressCallback callback = new RecordingProgressCallback();

        CodeIndexSummaryResDTO response = service.scanKnownFiles(scmConfig(), requestDTO,
                CodeIndexScanExecutionContext.progress(1002L, callback));

        assertEquals(CodeIndexConstants.ScanStatus.FAILED, response.getScanStatus());
        assertTrue(callback.events().stream()
                .anyMatch(event -> event.startsWith("failure:1002:未识别到 Maven 模块")));
        verify(codeIndexService).markScanFailed(eq(scmConfig()), eq(requestDTO), any());
        verify(codeIndexService, never()).saveSuccessfulIndex(any(), any(), any());
    }

    @Test
    @DisplayName("Diff 输入会过滤非索引文件，pom 变化时切换为 MODULE_RESCAN")
    void scanDiffFilesShouldAdaptDiffInput() {
        CodeIndexScanReqDTO requestDTO = request(List.of());
        DiffFile pomDiff = diff("pom.xml", false);
        DiffFile javaDiff = diff("src/main/java/com/example/DemoService.java", false);
        DiffFile deletedDiff = diff("src/main/java/com/example/OldService.java", true);
        CodeIndexSummaryResDTO expected = summary(CodeIndexConstants.ScanStatus.SUCCESS);
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any(RepositoryCodeIndexDraft.class)))
                .thenReturn(expected);

        CodeIndexSummaryResDTO response = service.scanDiffFiles(scmConfig(), requestDTO, List.of(pomDiff, javaDiff, deletedDiff));

        assertEquals(CodeIndexConstants.ScanStatus.SUCCESS, response.getScanStatus());
        ArgumentCaptor<CodeIndexScanReqDTO> requestCaptor = ArgumentCaptor.forClass(CodeIndexScanReqDTO.class);
        verify(codeIndexService).saveSuccessfulIndex(eq(scmConfig()), requestCaptor.capture(), any(RepositoryCodeIndexDraft.class));
        assertEquals(CodeIndexConstants.ScanType.MODULE_RESCAN, requestCaptor.getValue().getScanType());
        assertEquals(List.of("pom.xml", "src/main/java/com/example/DemoService.java"),
                requestCaptor.getValue().getFilePaths());
        assertEquals(List.of("src/main/java/com/example/OldService.java"),
                requestCaptor.getValue().getDeletedFilePaths());
    }

    private CodeIndexScanReqDTO request(List<String> filePaths) {
        CodeIndexScanReqDTO requestDTO = new CodeIndexScanReqDTO();
        requestDTO.setBranchName("main");
        requestDTO.setCommitSha("abc123");
        requestDTO.setScanType(CodeIndexConstants.ScanType.FULL);
        requestDTO.setFilePaths(filePaths);
        return requestDTO;
    }

    private ScmConfig scmConfig() {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(1L);
        scmConfig.setScmProvider("gitlab");
        scmConfig.setRepoOwner("quality");
        scmConfig.setRepoName("demo-service");
        return scmConfig;
    }

    private CodeIndexSummaryResDTO summary(String status) {
        CodeIndexSummaryResDTO response = new CodeIndexSummaryResDTO();
        response.setScanStatus(status);
        return response;
    }

    private DiffFile diff(String path, boolean deleted) {
        DiffFile diffFile = new DiffFile();
        diffFile.setNewPath(path);
        diffFile.setDeletedFile(deleted);
        return diffFile;
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

    private static String childPom(String artifactId) {
        return """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>%s</artifactId>
                    <version>1.0.0</version>
                </project>
                """.formatted(artifactId);
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

    private static class RecordingProgressCallback implements CodeIndexScanProgressCallback {

        private final List<String> events = new ArrayList<>();
        private final List<String> stageEvents = new ArrayList<>();

        @Override
        public void onStageStart(Long taskId, String scanStage, String stageMessage) {
            stageEvents.add(scanStage);
            events.add("stage:" + taskId + ":" + scanStage + ":" + stageMessage);
        }

        @Override
        public void onFileLoaded(Long taskId, int loadedFileCount) {
            events.add("file:" + taskId + ":" + loadedFileCount);
        }

        @Override
        public void onJavaParseProgress(Long taskId, int parsedFileCount, int totalJavaFileCount,
                                        int failedFileCount) {
            events.add("java:" + taskId + ":" + parsedFileCount + "/" + totalJavaFileCount
                    + "/" + failedFileCount);
        }

        @Override
        public void onPersisting(Long taskId, int classCount, int packageCount, int warningCount) {
            events.add("persist:" + taskId + ":" + classCount + "/" + packageCount + "/" + warningCount);
        }

        @Override
        public void onSuccess(Long taskId, Long resultIndexId, int classCount, int packageCount, int warningCount) {
            events.add("success:" + taskId + ":" + resultIndexId + ":" + classCount + "/"
                    + packageCount + "/" + warningCount);
        }

        @Override
        public void onFailure(Long taskId, String errorMessage) {
            events.add("failure:" + taskId + ":" + errorMessage);
        }

        private List<String> events() {
            return events;
        }

        private List<String> stageEvents() {
            return stageEvents;
        }
    }
}
