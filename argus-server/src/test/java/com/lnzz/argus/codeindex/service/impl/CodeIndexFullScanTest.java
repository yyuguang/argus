package com.lnzz.argus.codeindex.service.impl;

import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.scanner.RepositoryCodeIndexDraft;
import com.lnzz.argus.codeindex.service.CodeIndexService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.codeindex.support.ScmCodeIndexFileReader;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.model.PullRequestEvent;
import com.lnzz.argus.scm.service.ScmPlatformService;
import com.lnzz.argus.scm.service.ScmPlatformServiceFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeIndexScanServiceImpl - 首次全量扫描")
class CodeIndexFullScanTest {

    @Mock
    private CodeIndexService codeIndexService;

    @Test
    @DisplayName("标准 Maven 多模块全量扫描成功")
    void fullScanShouldBuildMultiModuleIndex() {
        CodeIndexScanServiceImpl service = service(Map.of(
                "pom.xml", rootPomWithModule(),
                "service/pom.xml", childPom("service"),
                "service/src/main/java/com/example/ChildService.java", """
                        package com.example;
                        public class ChildService {}
                        """
        ));
        CodeIndexScanReqDTO requestDTO = request(List.of(
                "pom.xml",
                "service/pom.xml",
                "service/src/main/java/com/example/ChildService.java"
        ));
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any()))
                .thenReturn(summary(CodeIndexConstants.ScanStatus.SUCCESS));

        CodeIndexSummaryResDTO response = service.scanFull(scmConfig(), requestDTO);

        assertEquals(CodeIndexConstants.ScanStatus.SUCCESS, response.getScanStatus());
        ArgumentCaptor<RepositoryCodeIndexDraft> captor = ArgumentCaptor.forClass(RepositoryCodeIndexDraft.class);
        verify(codeIndexService).saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), captor.capture());
        assertEquals(2, captor.getValue().getModuleCount());
        assertEquals(1, captor.getValue().getClassCount());
        assertEquals("service/src/main/java/com/example/ChildService.java",
                captor.getValue().getQualifiedNameToFilePath().get("com.example.ChildService"));
    }

    @Test
    @DisplayName("Java 解析失败时整体扫描成功但产生 warning")
    void fullScanShouldSucceedWithWarningWhenJavaParseFailed() {
        CodeIndexScanServiceImpl service = service(Map.of(
                "pom.xml", rootPom(),
                "src/main/java/com/example/Broken.java", "package com.example; public class Broken {"
        ));
        CodeIndexScanReqDTO requestDTO = request(List.of("pom.xml", "src/main/java/com/example/Broken.java"));
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any()))
                .thenReturn(summary(CodeIndexConstants.ScanStatus.SUCCESS));

        CodeIndexSummaryResDTO response = service.scanFull(scmConfig(), requestDTO);

        assertEquals(CodeIndexConstants.ScanStatus.SUCCESS, response.getScanStatus());
        ArgumentCaptor<RepositoryCodeIndexDraft> captor = ArgumentCaptor.forClass(RepositoryCodeIndexDraft.class);
        verify(codeIndexService).saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), captor.capture());
        assertEquals(0, captor.getValue().getClassCount());
        assertTrue(captor.getValue().getWarningCount() > 0);
    }

    @Test
    @DisplayName("全量扫描未传 filePaths 时会扫描仓库文件树")
    void fullScanShouldReadRepositoryTreeWhenFilePathsMissing() {
        CodeIndexScanServiceImpl service = service(Map.of(
                "pom.xml", rootPom(),
                "README.md", "# demo",
                "src/main/java/com/example/DemoService.java", """
                        package com.example;
                        public class DemoService {}
                        """
        ));
        CodeIndexScanReqDTO requestDTO = request(List.of());
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any()))
                .thenReturn(summary(CodeIndexConstants.ScanStatus.SUCCESS));

        CodeIndexSummaryResDTO response = service.scanFull(scmConfig(), requestDTO);

        assertEquals(CodeIndexConstants.ScanStatus.SUCCESS, response.getScanStatus());
        ArgumentCaptor<RepositoryCodeIndexDraft> captor = ArgumentCaptor.forClass(RepositoryCodeIndexDraft.class);
        verify(codeIndexService).saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), captor.capture());
        assertEquals(1, captor.getValue().getModuleCount());
        assertEquals(1, captor.getValue().getClassCount());
        assertEquals("src/main/java/com/example/DemoService.java",
                captor.getValue().getQualifiedNameToFilePath().get("com.example.DemoService"));
    }

    @Test
    @DisplayName("强制刷新全量扫描会转为 REBUILD 类型")
    void fullScanShouldUseRebuildTypeWhenForceRebuildEnabled() {
        CodeIndexScanServiceImpl service = service(Map.of(
                "pom.xml", rootPom(),
                "src/main/java/com/example/DemoService.java", """
                        package com.example;
                        public class DemoService {}
                        """
        ));
        CodeIndexScanReqDTO requestDTO = request(List.of("pom.xml", "src/main/java/com/example/DemoService.java"));
        requestDTO.setScanType(CodeIndexConstants.ScanType.FULL);
        requestDTO.setForceRebuild(true);
        when(codeIndexService.saveSuccessfulIndex(eq(scmConfig()), any(CodeIndexScanReqDTO.class), any()))
                .thenReturn(summary(CodeIndexConstants.ScanStatus.SUCCESS));

        service.scanFull(scmConfig(), requestDTO);

        ArgumentCaptor<CodeIndexScanReqDTO> requestCaptor = ArgumentCaptor.forClass(CodeIndexScanReqDTO.class);
        verify(codeIndexService).saveSuccessfulIndex(eq(scmConfig()), requestCaptor.capture(), any());
        assertEquals(CodeIndexConstants.ScanType.REBUILD, requestCaptor.getValue().getScanType());
    }

    private CodeIndexScanServiceImpl service(Map<String, String> files) {
        ScmCodeIndexFileReader reader = new ScmCodeIndexFileReader(new ScmPlatformServiceFactory(
                List.of(new FakeScmPlatformService(files))));
        return new CodeIndexScanServiceImpl(codeIndexService, reader);
    }

    private CodeIndexScanReqDTO request(List<String> filePaths) {
        CodeIndexScanReqDTO requestDTO = new CodeIndexScanReqDTO();
        requestDTO.setBranchName("main");
        requestDTO.setCommitSha("abc123");
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

    private static String rootPomWithModule() {
        return """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0.0</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>service</module>
                    </modules>
                </project>
                """;
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
        public List<String> listRepositoryFiles(ScmConfig config, String ref) {
            return List.copyOf(files.keySet());
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
