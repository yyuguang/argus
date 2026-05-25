package com.lnzz.argus.codeindex.scanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SourceRootDiscoverer - 源码根发现器")
class SourceRootDiscovererTest {

    private final SourceRootDiscoverer discoverer = new SourceRootDiscoverer();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("优先识别模块标准 src/main/java 源码根")
    void discoverStandardSourceRoot() throws IOException {
        Files.createDirectories(tempDir.resolve("service/src/main/java"));

        List<String> sourceRoots = discoverer.discover(tempDir, module("service"), List.of());

        assertEquals(List.of("service/src/main/java"), sourceRoots);
    }

    @Test
    @DisplayName("高级覆盖项作为补充追加在标准源码根之后")
    void overrideSourceRootSupplementsStandardRoot() throws IOException {
        Files.createDirectories(tempDir.resolve("service/src/main/java"));
        Files.createDirectories(tempDir.resolve("service/custom-src"));

        List<String> sourceRoots = discoverer.discover(tempDir, module("service"), List.of("service/custom-src"));

        assertEquals(List.of("service/src/main/java", "service/custom-src"), sourceRoots);
    }

    @Test
    @DisplayName("默认不把 target/generated-sources 识别为源码根")
    void targetGeneratedSourcesIsExcludedByDefault() throws IOException {
        Files.createDirectories(tempDir.resolve("service/target/generated-sources"));

        List<String> sourceRoots = discoverer.discover(tempDir, module("service"), List.of());

        assertTrue(sourceRoots.isEmpty());
    }

    private ModuleScanResult module(String modulePath) {
        ModuleScanResult module = new ModuleScanResult();
        module.setModuleName(modulePath);
        module.setModulePath(modulePath);
        module.setBuildType("MAVEN");
        module.setPackaging("jar");
        return module;
    }
}
