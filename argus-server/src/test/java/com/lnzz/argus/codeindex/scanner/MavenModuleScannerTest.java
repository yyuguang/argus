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

@DisplayName("MavenModuleScanner - Maven 模块扫描器")
class MavenModuleScannerTest {

    private final MavenModuleScanner scanner = new MavenModuleScanner();

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("无 modules 但存在 src/main/java 时识别为单模块")
    void scanSingleModuleRepository() throws IOException {
        writePom(tempDir, "demo-app", null, null);
        Files.createDirectories(tempDir.resolve("src/main/java/com/example"));

        List<ModuleScanResult> modules = scanner.scan(tempDir);

        assertEquals(1, modules.size());
        ModuleScanResult rootModule = modules.get(0);
        assertEquals("demo-app", rootModule.getModuleName());
        assertEquals("", rootModule.getModulePath());
        assertEquals("jar", rootModule.getPackaging());
        assertEquals("MAVEN", rootModule.getBuildType());
    }

    @Test
    @DisplayName("递归解析根 pom.xml 中声明的多模块")
    void scanMultiModuleRepository() throws IOException {
        writePom(tempDir, "demo-root", "pom", List.of("api", "service"));
        writePom(tempDir.resolve("api"), "demo-api", null, null);
        writePom(tempDir.resolve("service"), "demo-service", "war", null);

        List<ModuleScanResult> modules = scanner.scan(tempDir);

        assertEquals(3, modules.size());
        assertEquals("", modules.get(0).getModulePath());
        assertEquals("api", modules.get(1).getModulePath());
        assertEquals("", modules.get(1).getParentModulePath());
        assertEquals("service", modules.get(2).getModulePath());
        assertEquals("war", modules.get(2).getPackaging());
    }

    @Test
    @DisplayName("缺失根 pom.xml 时返回空模块列表")
    void missingRootPomReturnsEmptyList() {
        List<ModuleScanResult> modules = scanner.scan(tempDir);

        assertTrue(modules.isEmpty());
    }

    private void writePom(Path directory, String artifactId, String packaging, List<String> modules) throws IOException {
        Files.createDirectories(directory);
        StringBuilder content = new StringBuilder();
        content.append("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                """);
        content.append("    <artifactId>").append(artifactId).append("</artifactId>\n");
        content.append("    <version>1.0.0</version>\n");
        if (packaging != null) {
            content.append("    <packaging>").append(packaging).append("</packaging>\n");
        }
        if (modules != null && !modules.isEmpty()) {
            content.append("    <modules>\n");
            for (String module : modules) {
                content.append("        <module>").append(module).append("</module>\n");
            }
            content.append("    </modules>\n");
        }
        content.append("</project>\n");
        Files.writeString(directory.resolve("pom.xml"), content.toString());
    }
}
