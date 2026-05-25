package com.lnzz.argus.rule.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @classname: RuleDocumentPageItemResDTO
 * @author: Fantasy
 * @date: 2026/05/17 22:58
 * @description: 规则文档列表行响应，承载规则管理表格的最小展示字段。
 */
@Data
public class RuleDocumentPageItemResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则文档 ID。
     */
    private Long id;

    /**
     * 文档编码。
     */
    private String documentCode;

    /**
     * 文档名称。
     */
    private String documentName;

    /**
     * 规范分类。
     */
    private String category;

    /**
     * 作用域：GLOBAL/SCM。
     */
    private String scope;

    /**
     * SCM 仓库配置 ID。
     */
    private Long scmConfigId;

    /**
     * SCM 项目名称，便于前端表格直接展示。
     */
    private String scmProjectName;

    /**
     * 文档状态。
     */
    private String status;

    /**
     * 解析状态。
     */
    private String parseStatus;

    /**
     * 向量化状态。
     */
    private String vectorStatus;

    /**
     * 分块数量。
     */
    private Integer chunkCount;

    /**
     * 最近错误信息。
     */
    private String latestErrorMessage;

    /**
     * 最近更新时间。
     */
    private LocalDateTime updateTime;
}
