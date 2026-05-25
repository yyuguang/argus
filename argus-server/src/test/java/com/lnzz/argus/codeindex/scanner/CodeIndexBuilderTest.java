package com.lnzz.argus.codeindex.scanner;

import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("CodeIndexBuilder - 源码索引聚合器")
class CodeIndexBuilderTest {

    private final CodeIndexBuilder builder = new CodeIndexBuilder();

    @Test
    @DisplayName("聚合单模块索引统计与 FQN 定位映射")
    void buildSingleModuleDraft() {
        RepositoryCodeIndexDraft draft = builder.build(
                List.of(module("service", List.of("service/src/main/java"))),
                List.of(classIndex("service", "service/src/main/java/com/example/DemoService.java",
                        "com.example", "DemoService"))
        );

        assertEquals(1, draft.getModuleCount());
        assertEquals(1, draft.getSourceRootCount());
        assertEquals(1, draft.getJavaFileCount());
        assertEquals(1, draft.getClassCount());
        assertEquals("service/src/main/java/com/example/DemoService.java",
                draft.getQualifiedNameToFilePath().get("com.example.DemoService"));
    }

    @Test
    @DisplayName("跨模块同包名标记为 split package")
    void splitPackageIsMarkedAmbiguous() {
        RepositoryCodeIndexDraft draft = builder.build(
                List.of(
                        module("oms", List.of("oms/src/main/java")),
                        module("wms", List.of("wms/src/main/java"))
                ),
                List.of(
                        classIndex("oms", "oms/src/main/java/com/acme/order/OrderService.java",
                                "com.acme.shared", "OrderService"),
                        classIndex("wms", "wms/src/main/java/com/acme/order/WarehouseService.java",
                                "com.acme.shared", "WarehouseService")
                )
        );

        RepositoryCodeIndexDraft.PackageDraft packageDraft = draft.getPackages().get(0);
        assertEquals("com.acme.shared", packageDraft.getPackageName());
        assertTrue(packageDraft.getAmbiguous());
        assertEquals(List.of("oms", "wms"), packageDraft.getModulePaths());
        assertEquals(CodeIndexConstants.Confidence.MEDIUM, packageDraft.getConfidence());
    }

    @Test
    @DisplayName("重复 FQN 产生告警并保留首个定位映射")
    void duplicateQualifiedNameProducesWarning() {
        RepositoryCodeIndexDraft draft = builder.build(
                List.of(module("service", List.of("service/src/main/java"))),
                List.of(
                        classIndex("service", "service/src/main/java/com/example/DemoService.java",
                                "com.example", "DemoService"),
                        classIndex("service", "service/src/main/java/com/example/duplicate/DemoService.java",
                                "com.example", "DemoService")
                )
        );

        assertEquals("service/src/main/java/com/example/DemoService.java",
                draft.getQualifiedNameToFilePath().get("com.example.DemoService"));
        assertFalse(draft.getWarnings().isEmpty());
        assertTrue(draft.getWarnings().get(0).contains("重复全限定类名"));
    }

    private ModuleScanResult module(String modulePath, List<String> sourceRoots) {
        ModuleScanResult module = new ModuleScanResult();
        module.setModuleName(modulePath);
        module.setModulePath(modulePath);
        module.setSourceRoots(sourceRoots);
        module.setBuildType("MAVEN");
        module.setPackaging("jar");
        return module;
    }

    private JavaFileIndex classIndex(String modulePath, String filePath, String packageName, String className) {
        JavaFileIndex index = new JavaFileIndex();
        index.setModulePath(modulePath);
        index.setSourceRoot(modulePath + "/src/main/java");
        index.setFilePath(filePath);
        index.setPackageName(packageName);
        index.setClassName(className);
        index.setQualifiedName(packageName + "." + className);
        index.setClassKind(CodeIndexConstants.ClassKind.CLASS);
        index.setPrimaryType(true);
        index.setParserStatus(CodeIndexConstants.ScanStatus.SUCCESS);
        return index;
    }
}
