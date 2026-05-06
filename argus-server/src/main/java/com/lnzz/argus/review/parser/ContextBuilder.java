package com.lnzz.argus.review.parser;

import com.lnzz.argus.gitlab.client.GitLabApiClient;
import com.lnzz.argus.gitlab.model.DiffFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
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

    private static final int MAX_TOKENS_PER_FILE = 16000;
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+(com\\.lnzz\\.[\\w.]+);");

    private final GitLabApiClient gitLabApiClient;
    private final DiffParser diffParser;

    /**
     * M2-D01: 构建评审上下文列表
     *
     * @param projectId  项目ID
     * @param diffs      变更文件列表
     * @param ref        分支/commit
     * @return 评审上下文列表
     */
    public List<ReviewContext> buildReviewContexts(Long projectId, List<DiffFile> diffs, String ref) {
        List<ReviewContext> contexts = new ArrayList<>();

        for (DiffFile diff : diffs) {
            // 解析 diff
            diffParser.parseDiff(diff);

            // 获取完整文件内容
            String fullContent = gitLabApiClient.getFileContent(projectId, diff.getNewPath(), ref);

            // 提取新增行号
            List<Integer> addedLineNumbers = diff.getAddedLines() != null
                    ? diff.getAddedLines().stream().map(DiffFile.DiffLine::getLineNumber).collect(Collectors.toList())
                    : Collections.emptyList();

            // M2-C01: 获取关联类内容（从 import 中提取项目内部类）
            Map<String, String> relatedClasses = new LinkedHashMap<>();
            if (fullContent != null) {
                List<String> internalImports = extractInternalImports(fullContent);
                for (String importClass : internalImports) {
                    String importPath = classNameToPath(importClass);
                    String importContent = gitLabApiClient.getFileContent(projectId, importPath, ref);
                    if (importContent != null) {
                        // 只取类签名和方法签名，不取完整实现
                        relatedClasses.put(importClass, extractClassSummary(importContent));
                    }
                    // 限制关联类数量，避免 Token 超限
                    if (relatedClasses.size() >= 5) {
                        break;
                    }
                }
            }

            ReviewContext context = ReviewContext.builder()
                    .filePath(diff.getNewPath())
                    .fullContent(fullContent)
                    .diffContent(diff.getDiff())
                    .addedLineNumbers(addedLineNumbers)
                    .relatedClasses(relatedClasses)
                    .build();

            // Token 裁剪
            context.trimToMaxTokens(MAX_TOKENS_PER_FILE);

            contexts.add(context);
            log.debug("构建评审上下文: file={}, tokens≈{}", diff.getNewPath(), context.getEstimatedTokens());
        }

        return contexts;
    }

    /**
     * M2-C01: 从 import 语句中提取项目内部类
     */
    private List<String> extractInternalImports(String content) {
        List<String> imports = new ArrayList<>();
        Matcher matcher = IMPORT_PATTERN.matcher(content);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }
        return imports;
    }

    /**
     * M2-C06: 包名转文件路径
     */
    private String classNameToPath(String className) {
        return "src/main/java/" + className.replace('.', '/') + ".java";
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
}
