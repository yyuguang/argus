package com.lnzz.argus.review.parser;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * M2-D: 评审上下文
 * <p>送给 AI 引擎的结构化输入，包含代码、Diff 和关联类信息</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@Builder
public class ReviewContext {

    /** 文件路径 */
    private String filePath;

    /** 文件语言标签 */
    private String languageTag;

    /** 文件完整内容 */
    private String fullContent;

    /** Diff 内容 */
    private String diffContent;

    /** 新增的行 */
    private List<Integer> addedLineNumbers;

    /** 关联类内容（类名 → 内容摘要） */
    private Map<String, String> relatedClasses;

    /** 预估 Token 数 */
    private int estimatedTokens;

    /**
     * M2-D03: 预估 Token 数量（粗略：1 Token ≈ 4 字符）
     */
    public static int estimateTokens(String text) {
        if (text == null) {
            return 0;
        }
        return text.length() / 4;
    }

    /**
     * M2-D04: 裁剪上下文（超过 maxTokens 时截断完整文件内容）
     */
    public void trimToMaxTokens(int maxTokens) {
        int relatedTokens = 0;
        if (relatedClasses != null) {
            for (String relatedContent : relatedClasses.values()) {
                relatedTokens += estimateTokens(relatedContent);
            }
        }

        int currentTokens = estimateTokens(fullContent) + estimateTokens(diffContent) + relatedTokens;
        if (currentTokens <= maxTokens) {
            this.estimatedTokens = currentTokens;
            return;
        }

        // 优先保留 diff，其次保留部分关联类，最后裁剪完整文件内容。
        int reservedTokens = estimateTokens(diffContent) + Math.min(relatedTokens, maxTokens / 4);
        int maxFullContentChars = Math.max(0, (maxTokens - reservedTokens) * 4);
        if (maxFullContentChars > 0 && fullContent != null && fullContent.length() > maxFullContentChars) {
            this.fullContent = fullContent.substring(0, maxFullContentChars) + "\n// ... 文件过长，已截断 ...";
        }

        this.estimatedTokens = maxTokens;
    }
}
