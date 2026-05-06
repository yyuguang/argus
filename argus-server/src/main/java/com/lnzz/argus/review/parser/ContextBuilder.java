package com.lnzz.argus.review.parser;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.service.ScmPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * M2-D: 上下文构建器
 * <p>将 Diff + 文件内容 + 关联类组装为 AI 可理解的 ReviewContext</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private static final int DEFAULT_MAX_TOKENS_PER_FILE = 16000;
    private static final int DEFAULT_MAX_RELATED_CLASSES = 5;
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+([\\w.]+);");
    private static final String JAVA_SOURCE_SEGMENT = "/src/main/java/";

    private final DiffParser diffParser;

    /**
     * M2-D01: 构建评审上下文列表
     *
     * @param scmService SCM 服务
     * @param config     SCM 配置
     * @param task       评审任务
     * @param diffs      变更文件列表
     * @param ref        分支/commit
     * @return 评审上下文列表
     */
    public List<ReviewContext> buildReviewContexts(ScmPlatformService scmService,
                                                   ScmConfig config,
                                                   ReviewTask task,
                                                   List<DiffFile> diffs,
                                                   String ref) {
        List<ReviewContext> contexts = new ArrayList<>();
        Map<String, String> fileContentCache = new ConcurrentHashMap<>();
        Set<String> missingFileContentCache = ConcurrentHashMap.newKeySet();
        Map<String, String> resolvedPathCache = new ConcurrentHashMap<>();
        int maxRelatedClasses = Optional.ofNullable(config.getMaxRelatedClasses()).filter(v -> v > 0)
                .orElse(DEFAULT_MAX_RELATED_CLASSES);
        int maxContextTokens = Optional.ofNullable(config.getMaxContextTokens()).filter(v -> v > 0)
                .orElse(DEFAULT_MAX_TOKENS_PER_FILE);
        List<String> basePackages = resolveBasePackages(config, diffs);
        List<String> moduleSourceRoots = resolveModuleSourceRoots(config);
        List<PackageModuleRule> packageModuleRules = resolvePackageModuleRules(config);

        for (DiffFile diff : diffs) {
            // 解析 diff
            diffParser.parseDiff(diff);

            // 获取完整文件内容
            String fullContent = readFileContentWithCache(
                    fileContentCache, missingFileContentCache, scmService, config, task, diff.getNewPath(), ref);

            // 提取新增行号
            List<Integer> addedLineNumbers = diff.getAddedLines() != null
                    ? diff.getAddedLines().stream().map(DiffFile.DiffLine::getLineNumber).collect(Collectors.toList())
                    : Collections.emptyList();

            // M2-C01: 获取关联类内容（从 import 中提取项目内部类）
            Map<String, String> relatedClasses = new LinkedHashMap<>();
            if (fullContent != null && diff.isJavaFile()) {
                List<String> internalImports = extractInternalImports(fullContent, basePackages);
                for (String importClass : internalImports) {
                    String importContent = null;
                    String resolvedCacheKey = diff.getNewPath() + "::" + importClass;
                    String resolvedPath = resolvedPathCache.get(resolvedCacheKey);
                    if (resolvedPath != null) {
                        importContent = readFileContentWithCache(
                                fileContentCache, missingFileContentCache, scmService, config, task, resolvedPath, ref);
                    } else {
                        for (String importPath : resolveImportPaths(diff.getNewPath(), importClass, packageModuleRules, moduleSourceRoots)) {
                            importContent = readFileContentWithCache(
                                    fileContentCache, missingFileContentCache, scmService, config, task, importPath, ref);
                            if (importContent != null) {
                                resolvedPathCache.put(resolvedCacheKey, importPath);
                                break;
                            }
                        }
                    }
                    if (importContent != null) {
                        // 只取类签名和方法签名，不取完整实现
                        relatedClasses.put(importClass, extractClassSummary(importContent));
                    }
                    // 限制关联类数量，避免 Token 超限
                    if (relatedClasses.size() >= maxRelatedClasses) {
                        break;
                    }
                }
            }

            ReviewContext context = ReviewContext.builder()
                    .filePath(diff.getNewPath())
                    .languageTag(diff.getLanguageTag())
                    .fullContent(fullContent)
                    .diffContent(diff.getDiff())
                    .addedLineNumbers(addedLineNumbers)
                    .relatedClasses(relatedClasses)
                    .build();

            // Token 裁剪
            context.trimToMaxTokens(maxContextTokens);

            contexts.add(context);
            log.debug("构建评审上下文: file={}, tokens≈{}", diff.getNewPath(), context.getEstimatedTokens());
        }

        return contexts;
    }

    /**
     * M2-C01: 从 import 语句中提取项目内部类
     */
    private List<String> extractInternalImports(String content, List<String> basePackages) {
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(content);
        while (matcher.find()) {
            String importClass = matcher.group(1);
            if (matchesBasePackage(importClass, basePackages)) {
                imports.add(importClass);
            }
        }
        return imports;
    }

    /**
     * M2-C06: 包名转文件路径
     */
    private List<String> resolveImportPaths(String currentFilePath,
                                            String className,
                                            List<PackageModuleRule> packageModuleRules,
                                            List<String> moduleSourceRoots) {
        String relativeJavaPath = className.replace('.', '/') + ".java";
        Set<String> candidates = new LinkedHashSet<>();

        if (currentFilePath != null && currentFilePath.contains(JAVA_SOURCE_SEGMENT)) {
            String modulePrefix = currentFilePath.substring(0, currentFilePath.indexOf(JAVA_SOURCE_SEGMENT));
            if (!modulePrefix.isBlank()) {
                candidates.add(modulePrefix + JAVA_SOURCE_SEGMENT + relativeJavaPath);
            }
        }

        for (PackageModuleRule rule : packageModuleRules) {
            if (rule.packagePrefix() != null && className.startsWith(rule.packagePrefix())) {
                candidates.add(normalizeSourceRoot(rule.sourceRoot()) + "/" + relativeJavaPath);
            }
        }

        for (String sourceRoot : moduleSourceRoots) {
            candidates.add(normalizeSourceRoot(sourceRoot) + "/" + relativeJavaPath);
        }

        candidates.add("src/main/java/" + relativeJavaPath);
        return new ArrayList<>(candidates);
    }

    private List<String> resolveBasePackages(ScmConfig config, List<DiffFile> diffs) {
        List<String> configured = parseStringArray(config.getBasePackages());
        if (!configured.isEmpty()) {
            return configured;
        }
        for (DiffFile diff : diffs) {
            String basePackage = inferBasePackageFromPath(diff.getNewPath());
            if (basePackage != null) {
                return List.of(basePackage);
            }
        }
        return List.of();
    }

    private List<String> resolveModuleSourceRoots(ScmConfig config) {
        List<String> configured = parseStringArray(config.getModuleSourceRoots());
        if (!configured.isEmpty()) {
            return configured;
        }
        return List.of("src/main/java");
    }

    private List<PackageModuleRule> resolvePackageModuleRules(ScmConfig config) {
        if (config.getPackageModuleMappings() == null || config.getPackageModuleMappings().isBlank()) {
            return List.of();
        }
        try {
            return JSON.parseArray(config.getPackageModuleMappings(), PackageModuleRule.class);
        } catch (Exception e) {
            log.warn("解析 packageModuleMappings 失败, raw={}", config.getPackageModuleMappings(), e);
            return List.of();
        }
    }

    private List<String> parseStringArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return JSON.parseArray(raw, String.class).stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
        } catch (Exception e) {
            log.warn("解析配置数组失败, raw={}", raw, e);
            return List.of();
        }
    }

    private boolean matchesBasePackage(String importClass, List<String> basePackages) {
        if (basePackages == null || basePackages.isEmpty()) {
            return false;
        }
        return basePackages.stream().anyMatch(importClass::startsWith);
    }

    private String readFileContentWithCache(Map<String, String> cache,
                                            Set<String> missingCache,
                                            ScmPlatformService scmService,
                                            ScmConfig config,
                                            ReviewTask task,
                                            String filePath,
                                            String ref) {
        String cacheKey = ref + "::" + filePath;
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        if (missingCache.contains(cacheKey)) {
            return null;
        }
        String content = scmService.getFileContent(config, task, filePath, ref);
        if (content == null) {
            missingCache.add(cacheKey);
            return null;
        }
        cache.put(cacheKey, content);
        return content;
    }

    private String normalizeSourceRoot(String sourceRoot) {
        if (sourceRoot == null || sourceRoot.isBlank()) {
            return "src/main/java";
        }
        return sourceRoot.endsWith("/") ? sourceRoot.substring(0, sourceRoot.length() - 1) : sourceRoot;
    }

    private String inferBasePackageFromPath(String filePath) {
        if (filePath == null || !filePath.contains(JAVA_SOURCE_SEGMENT)) {
            return null;
        }
        String relative = filePath.substring(filePath.indexOf(JAVA_SOURCE_SEGMENT) + JAVA_SOURCE_SEGMENT.length());
        if (!relative.endsWith(".java")) {
            return null;
        }
        String packageName = relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot <= 0) {
            return null;
        }
        String classPackage = packageName.substring(0, lastDot);
        String[] segments = classPackage.split("\\.");
        if (segments.length >= 3) {
            return String.join(".", segments[0], segments[1], segments[2]);
        }
        return classPackage;
    }

    /**
     * 提取类摘要（类签名 + 方法签名，不含实现）
     */
    private String extractClassSummary(String content) {
        StringBuilder summary = new StringBuilder();
        String[] lines = content.split("\n");
        boolean inMethod = false;
        int braceDepth = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            // 保留 package、import、类声明、注解、字段声明
            if (trimmed.startsWith("package ") || trimmed.startsWith("import ")
                    || trimmed.startsWith("@") || trimmed.startsWith("public class ")
                    || trimmed.startsWith("public interface ") || trimmed.startsWith("private ")
                    || trimmed.startsWith("protected ") || trimmed.startsWith("public ")) {

                if (!inMethod) {
                    summary.append(line).append("\n");
                }
            }

            // 跟踪大括号深度
            for (char c : line.toCharArray()) {
                if (c == '{') {
                    braceDepth++;
                    if (braceDepth == 2) {
                        inMethod = true;
                    }
                }
                if (c == '}') {
                    braceDepth--;
                    if (braceDepth <= 1) {
                        inMethod = false;
                    }
                }
            }

            // 限制摘要长度
            if (summary.length() > 2000) {
                summary.append("// ... 摘要截断 ...\n");
                break;
            }
        }

        return summary.toString();
    }

    private record PackageModuleRule(String packagePrefix, String sourceRoot) {
    }
}
