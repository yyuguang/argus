package com.lnzz.argus.codeindex.scanner;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * @classname: JavaSourceIndexParser
 * @author: Fantasy
 * @date: 2026/05/19 16:55
 * @description: Java 源码索引解析器，基于 JavaParser 提取 package、imports 和顶层类型信息。
 */
@Slf4j
public class JavaSourceIndexParser {

    private static final String PARSER_STATUS_SUCCESS = "SUCCESS";
    private static final String PARSER_STATUS_FAILED = "FAILED";

    private final JavaParser javaParser;

    public JavaSourceIndexParser() {
        ParserConfiguration parserConfiguration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        this.javaParser = new JavaParser(parserConfiguration);
    }

    JavaSourceIndexParser(JavaParser javaParser) {
        this.javaParser = javaParser;
    }

    /**
     * 解析仓库中的 Java 文件。
     *
     * @param repositoryRoot 仓库根目录
     * @param modulePath 模块路径
     * @param sourceRoot 源码根路径
     * @param javaFile Java 文件路径
     * @return Java 文件索引结果
     */
    public List<JavaFileIndex> parse(Path repositoryRoot, String modulePath, String sourceRoot, Path javaFile) {
        String filePath = normalizeRelativePath(repositoryRoot, javaFile);
        try {
            String sourceText = Files.readString(javaFile, StandardCharsets.UTF_8);
            return parse(modulePath, sourceRoot, filePath, sourceText);
        } catch (IOException e) {
            return List.of(failedIndex(modulePath, sourceRoot, filePath, "", "读取 Java 文件失败: " + e.getMessage()));
        }
    }

    /**
     * 解析 Java 源码文本。
     *
     * @param modulePath 模块路径
     * @param sourceRoot 源码根路径
     * @param filePath 文件相对仓库路径
     * @param sourceText Java 源码文本
     * @return Java 文件索引结果
     */
    public List<JavaFileIndex> parse(String modulePath, String sourceRoot, String filePath, String sourceText) {
        String normalizedSourceText = sourceText == null ? "" : sourceText;
        try {
            ParseResult<CompilationUnit> parseResult = javaParser.parse(normalizedSourceText);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                return List.of(failedIndex(modulePath, sourceRoot, filePath, normalizedSourceText, parseResult.getProblems().toString()));
            }
            CompilationUnit compilationUnit = parseResult.getResult().orElseThrow();
            String packageName = compilationUnit.getPackageDeclaration()
                    .map(packageDeclaration -> packageDeclaration.getName().asString())
                    .orElse("");
            List<String> imports = compilationUnit.getImports().stream()
                    .map(this::formatImport)
                    .toList();
            return compilationUnit.getTypes().stream()
                    .map(typeDeclaration -> buildIndex(modulePath, sourceRoot, filePath, normalizedSourceText,
                            packageName, imports, typeDeclaration))
                    .toList();
        } catch (Throwable e) {
            if (isFatal(e)) {
                throw (Error) e;
            }
            // JavaParser 在少量语法/符号边界下可能抛 AssertionError；降级到单文件失败，避免整仓扫描中断。
            log.warn("Java 文件解析异常，已降级为单文件 FAILED, filePath={}, errorType={}, message={}",
                    filePath, e.getClass().getSimpleName(), e.getMessage());
            return List.of(failedIndex(modulePath, sourceRoot, filePath, normalizedSourceText, formatParserError(e)));
        }
    }

    private JavaFileIndex buildIndex(String modulePath, String sourceRoot, String filePath, String sourceText,
                                     String packageName, List<String> imports, TypeDeclaration<?> typeDeclaration) {
        JavaFileIndex index = new JavaFileIndex();
        index.setModulePath(defaultString(modulePath));
        index.setSourceRoot(defaultString(sourceRoot));
        index.setFilePath(defaultString(filePath));
        index.setFileSha(sha256(sourceText));
        index.setPackageName(packageName);
        index.setClassName(typeDeclaration.getNameAsString());
        index.setQualifiedName(qualifiedName(packageName, typeDeclaration.getNameAsString()));
        index.setClassKind(resolveClassKind(typeDeclaration));
        index.setPrimaryType(primaryType(filePath, typeDeclaration.getNameAsString()));
        index.setLineStart(typeDeclaration.getBegin().map(position -> position.line).orElse(null));
        index.setLineEnd(typeDeclaration.getEnd().map(position -> position.line).orElse(null));
        index.setImports(imports);
        index.setParserStatus(PARSER_STATUS_SUCCESS);
        return index;
    }

    private JavaFileIndex failedIndex(String modulePath, String sourceRoot, String filePath, String sourceText, String errorMessage) {
        JavaFileIndex index = new JavaFileIndex();
        index.setModulePath(defaultString(modulePath));
        index.setSourceRoot(defaultString(sourceRoot));
        index.setFilePath(defaultString(filePath));
        index.setFileSha(sha256(sourceText));
        index.setPackageName("");
        index.setParserStatus(PARSER_STATUS_FAILED);
        index.setPrimaryType(false);
        index.setErrorMessage(errorMessage);
        return index;
    }

    private String resolveClassKind(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration classOrInterfaceDeclaration) {
            return classOrInterfaceDeclaration.isInterface()
                    ? CodeIndexConstants.ClassKind.INTERFACE
                    : CodeIndexConstants.ClassKind.CLASS;
        }
        if (typeDeclaration instanceof EnumDeclaration) {
            return CodeIndexConstants.ClassKind.ENUM;
        }
        if (typeDeclaration instanceof AnnotationDeclaration) {
            return CodeIndexConstants.ClassKind.ANNOTATION;
        }
        if (typeDeclaration instanceof RecordDeclaration) {
            return CodeIndexConstants.ClassKind.RECORD;
        }
        return CodeIndexConstants.ClassKind.CLASS;
    }

    private String formatImport(ImportDeclaration importDeclaration) {
        StringBuilder builder = new StringBuilder();
        if (importDeclaration.isStatic()) {
            builder.append("static ");
        }
        builder.append(importDeclaration.getNameAsString());
        if (importDeclaration.isAsterisk()) {
            builder.append(".*");
        }
        return builder.toString();
    }

    private boolean primaryType(String filePath, String className) {
        String normalizedFilePath = defaultString(filePath);
        int slashIndex = normalizedFilePath.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalizedFilePath.substring(slashIndex + 1) : normalizedFilePath;
        int dotIndex = fileName.lastIndexOf('.');
        String mainName = dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        return mainName.equals(className);
    }

    private String qualifiedName(String packageName, String className) {
        return isBlank(packageName) ? className : packageName + "." + className;
    }

    private String normalizeRelativePath(Path repositoryRoot, Path path) {
        if (repositoryRoot == null || path == null) {
            return "";
        }
        return repositoryRoot.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private String sha256(String sourceText) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(defaultString(sourceText).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private String formatParserError(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (isBlank(message) ? "" : ": " + message);
    }

    private boolean isFatal(Throwable throwable) {
        return throwable instanceof VirtualMachineError || throwable instanceof ThreadDeath;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
