package com.lnzz.argus.rule.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @classname: RuleDocument
 * @author: Fantasy
 * @date: 2026/05/17 22:20
 * @description: 规则文档主表实体，承载规范文档导入后的元数据、解析状态和向量化状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_rule_document")
public class RuleDocument extends BaseEntity {

    /**
     * 文档编码，规则管理域内唯一。
     */
    private String documentCode;

    /**
     * 文档名称。
     */
    private String documentName;

    /**
     * 规范分类，如 CODING/API/DB/SERVICE。
     */
    private String category;

    /**
     * 作用域：GLOBAL/SCM。
     */
    private String scope;

    /**
     * SCM 仓库配置 ID，scope=SCM 时使用。
     */
    private Long scmConfigId;

    /**
     * 来源类型：UPLOAD/MANUAL/MIGRATION。
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
     * 文档状态：DRAFT/ACTIVE/DISABLED/ARCHIVED。
     */
    private String status;

    /**
     * 解析状态：PENDING/SUCCESS/FAILED。
     */
    private String parseStatus;

    /**
     * 向量化状态：PENDING/SUCCESS/FAILED。
     */
    private String vectorStatus;

    /**
     * 解析后的全文文本。
     */
    private String contentText;

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
     * 是否软删除。
     */
    private Boolean isDeleted;

    /**
     * 乐观锁版本。
     */
    @Version
    private Integer version;
}
