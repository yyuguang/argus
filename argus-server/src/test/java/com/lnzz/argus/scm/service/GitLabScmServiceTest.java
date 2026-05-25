package com.lnzz.argus.scm.service;

import com.lnzz.argus.config.ScmProperties;
import com.lnzz.argus.scm.entity.ScmConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GitLabScmService - repository archive 物化")
class GitLabScmServiceTest {

    @TempDir
    Path repositoryRoot;

    @Test
    @DisplayName("archive zip 解压时剥离根目录并只物化可索引文件")
    void materializeRepositoryFilesShouldExtractOnlyIndexFiles() throws IOException {
        GitLabScmService service = new ArchiveGitLabScmService(scmProperties(), archiveBytes());
        List<String> loadedFilePaths = new ArrayList<>();
        List<String> failedFilePaths = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        boolean materialized = service.materializeRepositoryFiles(
                scmConfig(), "master", path -> path.endsWith(".java") || path.endsWith("pom.xml"),
                repositoryRoot, loadedFilePaths, failedFilePaths, warnings);

        assertTrue(materialized);
        assertEquals(List.of("pom.xml", "src/main/java/com/example/Demo.java"), loadedFilePaths);
        assertEquals(List.of("../evil.java"), failedFilePaths);
        assertEquals(1, warnings.size());
        assertTrue(Files.exists(repositoryRoot.resolve("pom.xml")));
        assertTrue(Files.exists(repositoryRoot.resolve("src/main/java/com/example/Demo.java")));
        assertTrue(Files.notExists(repositoryRoot.resolve("README.md")));
        assertTrue(Files.notExists(repositoryRoot.getParent().resolve("evil.java")));
    }

    private ScmConfig scmConfig() {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setScmProvider("gitlab");
        scmConfig.setProjectId(882L);
        scmConfig.setAccessToken("test-token");
        scmConfig.setApiBaseUrl("http://gitlab.example.com/api/v4");
        return scmConfig;
    }

    private ScmProperties scmProperties() {
        ScmProperties properties = new ScmProperties();
        properties.getGitlab().setMaxArchiveSize(1024 * 1024);
        return properties;
    }

    private byte[] archiveBytes() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            addEntry(zipOutputStream, "demo-master/pom.xml", "<project><modelVersion>4.0.0</modelVersion></project>");
            addEntry(zipOutputStream, "demo-master/README.md", "# demo");
            addEntry(zipOutputStream, "demo-master/src/main/java/com/example/Demo.java",
                    "package com.example; public class Demo {}");
            addEntry(zipOutputStream, "demo-master/../evil.java", "package evil;");
        }
        return outputStream.toByteArray();
    }

    private void addEntry(ZipOutputStream zipOutputStream, String path, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(path));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static class ArchiveGitLabScmService extends GitLabScmService {

        private final byte[] archiveBytes;

        private ArchiveGitLabScmService(ScmProperties scmProperties, byte[] archiveBytes) {
            super(scmProperties);
            this.archiveBytes = archiveBytes;
        }

        @Override
        protected byte[] downloadRepositoryArchive(ScmConfig config, String ref) {
            return archiveBytes;
        }
    }
}
