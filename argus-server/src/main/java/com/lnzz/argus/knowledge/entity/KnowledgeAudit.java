package com.lnzz.argus.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识操作留痕实体（M8-A04）
 * <p>记录对知识条目的每一次人工操作，支持审计追溯</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_knowledge_audit")
public class KnowledgeAudit extends BaseEntity {

    /** 知识条目ID */
    private Long knowledgeEntryId;

    /** 操作类型: CONFIRM / MARK_FALSE_POSITIVE / IGNORE / UPDATE / DELETE / PROMOTE_WHITELIST / DEMOTE_WHITELIST */
    private String action;

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String comment;

    /** 操作前状态 */
    private String beforeStatus;

    /** 操作后状态 */
    private String afterStatus;
}
