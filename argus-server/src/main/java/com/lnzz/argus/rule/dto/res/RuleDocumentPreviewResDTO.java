package com.lnzz.argus.rule.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @classname: RuleDocumentPreviewResDTO
 * @author: Fantasy
 * @date: 2026/05/17 22:58
 * @description: 规则文档预览响应，承载解析纯文本、分块信息和最近处理状态。
 */
@Data
public class RuleDocumentPreviewResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 解析后的纯文本。
     */
    private String plainText;

    /**
     * 分块预览列表。
     */
    private List<ChunkPreviewDTO> chunks = new ArrayList<>();

    /**
     * 解析状态。
     */
    private String parseStatus;

    /**
     * 向量化状态。
     */
    private String vectorStatus;

    /**
     * 最近错误信息。
     */
    private String latestErrorMessage;

    /**
     * 分块预览项。
     */
    @Data
    public static class ChunkPreviewDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 分块 ID。
         */
        private Long id;

        /**
         * 分块序号。
         */
        private Integer chunkNo;

        /**
         * 分块标题。
         */
        private String title;

        /**
         * 分块文本。
         */
        private String contentText;

        /**
         * Token 预估值。
         */
        private Integer tokenEstimate;

        /**
         * 分块状态。
         */
        private String status;
    }
}
