package com.lnzz.argus.rule.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @classname: RuleDocumentDetailResDTO
 * @author: Fantasy
 * @date: 2026/05/17 22:58
 * @description: 规则文档详情响应，只表达文档元数据和状态信息。
 */
@Data
public class RuleDocumentDetailResDTO implements Serializable {

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
     * 来源类型。
     */
    private String sourceType;

    /**
     * 原始文件名。
     */
    private String fileName;

    /**
     * 文件扩展名。
     */
    private String fileExt;

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
     * 文档摘要。
     */
    private String summaryText;

    /**
     * 分块数量。
     */
    private Integer chunkCount;

    /**
     * 规则文档业务版本号。
     */
    private Integer versionNo;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 最近错误信息。
     */
    private String latestErrorMessage;

    /**
     * 创建人。
     */
    private String createBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 修改人。
     */
    private String updateBy;

    /**
     * 修改时间。
     */
    private LocalDateTime updateTime;
}
