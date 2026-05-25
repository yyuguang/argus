package com.lnzz.argus.codeindex.support;

import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.model.PullRequestEvent;
import com.lnzz.argus.scm.service.ScmPlatformService;
import com.lnzz.argus.scm.service.ScmPlatformServiceFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ScmCodeIndexFileReader - SCM 已知路径文件读取")
class ScmCodeIndexFileReaderTest {

    @Test
    @DisplayName("按已知路径读取 SCM 文件并写入临时工作区")
    void readKnownFilesShouldMaterializeWorkspace() throws IOException {
        ScmCodeIndexFileReader reader = reader(Map.of(
                "pom.xml", "<project><modelVersion>4.0.0</modelVersion></project>",
                "src/main/java/com/example/Demo.java", "package com.example; public class Demo {}"
        ));

        try (ScmCodeIndexWorkspace workspace = reader.readKnownFiles(scmConfig(), "abc123",
                List.of("pom.xml", "src/main/java/com/example/Demo.java"))) {
            assertEquals(2, workspace.getLoadedFilePaths().size());
            assertTrue(Files.exists(workspace.getRepositoryRoot().resolve("pom.xml")));
            assertTrue(Files.exists(workspace.getRepositoryRoot().resolve("src/main/java/com/example/Demo.java")));
        }
    }

    @Test
    @DisplayName("仓库扫描会先读取文件树并只物化可索引文件")
    void readRepositoryFilesShouldMaterializeOnlyIndexFiles() throws IOException {
        ScmCodeIndexFileReader reader = reader(Map.of(
                "pom.xml", "<project><modelVersion>4.0.0</modelVersion></project>",
                "README.md", "# demo",
                "src/main/java/com/example/Demo.java", "package com.example; public class Demo {}"
        ));

        try (ScmCodeIndexWorkspace workspace = reader.readRepositoryFiles(
                scmConfig(), "main", path -> path.endsWith(".java") || path.endsWith("pom.xml"))) {
            assertEquals(2, workspace.getLoadedFilePaths().size());
            assertTrue(Files.exists(workspace.getRepositoryRoot().resolve("pom.xml")));
            assertTrue(Files.exists(workspace.getRepositoryRoot().resolve("src/main/java/com/example/Demo.java")));
            assertTrue(Files.notExists(workspace.getRepositoryRoot().resolve("README.md")));
        }
    }

    @Test
    @DisplayName("平台支持仓库快照时优先物化快照而不是逐文件读取 raw")
    void readRepositoryFilesShouldPreferRepositorySnapshot() throws IOException {
        SnapshotScmPlatformService platformService = new SnapshotScmPlatformService(Map.of(
                "pom.xml", "<project><modelVersion>4.0.0</modelVersion></project>",
                "README.md", "# demo",
                "src/main/java/com/example/Demo.java", "package com.example; public class Demo {}"
        ));
        ScmCodeIndexFileReader reader = new ScmCodeIndexFileReader(
                new ScmPlatformServiceFactory(List.of(platformService)));

        try (ScmCodeIndexWorkspace workspace = reader.readRepositoryFiles(
                scmConfig(), "main", path -> path.endsWith(".java") || path.endsWith("pom.xml"))) {
            assertTrue(platformService.snapshotUsed);
            assertEquals(0, platformService.rawReadCount);
            assertEquals(2, workspace.getLoadedFilePaths().size());
            assertTrue(Files.exists(workspace.getRepositoryRoot().resolve("pom.xml")));
            assertTrue(Files.exists(workspace.getRepositoryRoot().resolve("src/main/java/com/example/Demo.java")));
            assertTrue(Files.notExists(workspace.getRepositoryRoot().resolve("README.md")));
        }
    }

    @Test
    @DisplayName("单个文件读取失败时记录失败路径和告警，不阻断其他文件")
    void readKnownFilesShouldRecordFailedPath() throws IOException {
        ScmCodeIndexFileReader reader = reader(Map.of("pom.xml", "<project/>"));

        try (ScmCodeIndexWorkspace workspace = reader.readKnownFiles(scmConfig(), "abc123",
                List.of("pom.xml", "src/main/java/com/example/Missing.java"))) {
            assertEquals(List.of("pom.xml"), workspace.getLoadedFilePaths());
            assertEquals(List.of("src/main/java/com/example/Missing.java"), workspace.getFailedFilePaths());
            assertEquals(1, workspace.getWarnings().size());
        }
    }

    @Test
    @DisplayName("非法路径会被拒绝，避免写出临时工作区")
    void illegalPathShouldBeRejected() throws IOException {
        ScmCodeIndexFileReader reader = reader(Map.of());

        try (ScmCodeIndexWorkspace workspace = reader.readKnownFiles(scmConfig(), "abc123",
                List.of("../secret.java"))) {
            assertEquals(List.of("../secret.java"), workspace.getFailedFilePaths());
            assertTrue(workspace.getWarnings().get(0).contains("非法文件路径"));
        }
    }

    private ScmCodeIndexFileReader reader(Map<String, String> files) {
        return new ScmCodeIndexFileReader(new ScmPlatformServiceFactory(List.of(new FakeScmPlatformService(files))));
    }

    private ScmConfig scmConfig() {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setScmProvider("gitlab");
        return scmConfig;
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

    private static class SnapshotScmPlatformService extends FakeScmPlatformService {

        private final Map<String, String> files;
        private boolean snapshotUsed;
        private int rawReadCount;

        private SnapshotScmPlatformService(Map<String, String> files) {
            super(files);
            this.files = files;
        }

        @Override
        public String getFileContent(ScmConfig config, String filePath, String ref) {
            rawReadCount++;
            throw new AssertionError("不应逐文件读取 raw 内容");
        }

        @Override
        public boolean materializeRepositoryFiles(ScmConfig config,
                                                  String ref,
                                                  Predicate<String> fileFilter,
                                                  Path repositoryRoot,
                                                  Collection<String> loadedFilePaths,
                                                  Collection<String> failedFilePaths,
                                                  Collection<String> warnings) {
            snapshotUsed = true;
            files.forEach((filePath, content) -> {
                if (fileFilter != null && !fileFilter.test(filePath)) {
                    return;
                }
                Path targetPath = repositoryRoot.resolve(filePath).normalize();
                try {
                    Files.createDirectories(targetPath.getParent());
                    Files.writeString(targetPath, content, StandardCharsets.UTF_8);
                    loadedFilePaths.add(filePath);
                } catch (IOException e) {
                    failedFilePaths.add(filePath);
                    warnings.add("快照文件写入失败: " + filePath);
                }
            });
            return true;
        }
    }
}
