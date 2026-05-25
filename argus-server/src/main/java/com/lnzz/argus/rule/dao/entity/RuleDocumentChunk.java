package com.lnzz.argus.rule.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @classname: RuleDocumentChunk
 * @author: Fantasy
 * @date: 2026/05/17 22:20
 * @description: 规则文档分块实体，承载向量检索使用的分段文本和状态信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_rule_document_chunk")
public class RuleDocumentChunk extends BaseEntity {

    /**
     * 所属规则文档 ID。
     */
    private Long documentId;

    /**
     * 分块序号，从 1 开始递增。
     */
    private Integer chunkNo;

    /**
     * 分块标题。
     */
    private String title;

    /**
     * 分块文本内容。
     */
    private String contentText;

    /**
     * Token 预估值。
     */
    private Integer tokenEstimate;

    /**
     * 向量库文档 ID。
     */
    private String vectorDocId;

    /**
     * 分块状态：ACTIVE/DISABLED。
     */
    private String status;

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
