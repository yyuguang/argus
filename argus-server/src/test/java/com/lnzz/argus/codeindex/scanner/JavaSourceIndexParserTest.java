package com.lnzz.argus.codeindex.scanner;

import com.github.javaparser.JavaParser;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JavaSourceIndexParser - Java 文件解析器")
class JavaSourceIndexParserTest {

    private final JavaSourceIndexParser parser = new JavaSourceIndexParser();

    @Test
    @DisplayName("解析普通类的 package、imports、FQN 与行号")
    void parseClassSource() {
        List<JavaFileIndex> indexes = parser.parse("service", "service/src/main/java",
                "service/src/main/java/com/example/DemoService.java", """
                        package com.example;

                        import java.util.List;

                        public class DemoService {
                            private List<String> names;
                        }
                        """);

        assertEquals(1, indexes.size());
        JavaFileIndex index = indexes.get(0);
        assertEquals("SUCCESS", index.getParserStatus());
        assertEquals("com.example", index.getPackageName());
        assertEquals("DemoService", index.getClassName());
        assertEquals("com.example.DemoService", index.getQualifiedName());
        assertEquals(CodeIndexConstants.ClassKind.CLASS, index.getClassKind());
        assertEquals(List.of("java.util.List"), index.getImports());
        assertEquals(5, index.getLineStart());
        assertEquals(7, index.getLineEnd());
    }

    @Test
    @DisplayName("解析 interface、enum、annotation 和 record")
    void parseDifferentTopLevelKinds() {
        assertEquals(CodeIndexConstants.ClassKind.INTERFACE, firstKind("public interface DemoApi {}"));
        assertEquals(CodeIndexConstants.ClassKind.ENUM, firstKind("public enum DemoEnum { A }"));
        assertEquals(CodeIndexConstants.ClassKind.ANNOTATION, firstKind("public @interface DemoAnnotation {}"));
        assertEquals(CodeIndexConstants.ClassKind.RECORD, firstKind("public record DemoRecord(String name) {}"));
    }

    @Test
    @DisplayName("无 package 文件使用简单类名作为全限定名")
    void parseSourceWithoutPackage() {
        List<JavaFileIndex> indexes = parser.parse("", "src/main/java",
                "src/main/java/DefaultDemo.java", "public class DefaultDemo {}");

        assertEquals("", indexes.get(0).getPackageName());
        assertEquals("DefaultDemo", indexes.get(0).getQualifiedName());
    }

    @Test
    @DisplayName("语法不完整文件返回 FAILED 结果且不抛出异常")
    void incompleteSourceReturnsFailedIndex() {
        List<JavaFileIndex> indexes = parser.parse("service", "service/src/main/java",
                "service/src/main/java/com/example/Broken.java", "package com.example; public class Broken {");

        assertEquals(1, indexes.size());
        assertEquals("FAILED", indexes.get(0).getParserStatus());
        assertFalse(indexes.get(0).getErrorMessage().isBlank());
    }

    @Test
    @DisplayName("JavaParser 内部断言错误降级为单文件 FAILED 结果")
    void parserInternalErrorShouldReturnFailedIndex() {
        JavaParser javaParser = mock(JavaParser.class);
        when(javaParser.parse(anyString())).thenThrow(new AssertionError("A reference was unexpectedly null."));
        JavaSourceIndexParser guardedParser = new JavaSourceIndexParser(javaParser);

        List<JavaFileIndex> indexes = guardedParser.parse("service", "service/src/main/java",
                "service/src/main/java/com/example/BrokenByParser.java", "package com.example; public class BrokenByParser {}");

        assertEquals(1, indexes.size());
        assertEquals("FAILED", indexes.get(0).getParserStatus());
        assertTrue(indexes.get(0).getErrorMessage().contains("AssertionError"));
    }

    private String firstKind(String source) {
        return parser.parse("", "src/main/java", "src/main/java/Demo.java", source)
                .get(0)
                .getClassKind();
    }
}
