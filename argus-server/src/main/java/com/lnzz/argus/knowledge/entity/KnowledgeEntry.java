package com.lnzz.argus.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 知识条目实体（M8）
 * <p>沉淀错误模式、根因和修复经验，支持人工确认、误报标记和白名单管理</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_knowledge_entry")
public class KnowledgeEntry extends BaseEntity {

    /** 错误指纹 */
    private String errorFingerprint;

    /** 错误类型 */
    private String errorType;

    /** 应用名称 */
    private String appName;

    /** 知识标题 */
    private String title;

    /** 错误模式描述 */
    private String errorPattern;

    /** 根因 */
    private String rootCause;

    /** 修复建议 */
    private String fixSuggestion;

    /** 预防建议 */
    private String preventionAdvice;

    /** 来源错误事件ID */
    private Long sourceEventId;

    /** 来源分析结果ID */
    private Long sourceAnalysisId;

    /** 状态: DRAFT / CONFIRMED / FALSE_POSITIVE / OUTDATED / WHITELIST */
    private String status;

    /** 来源: AUTO / MANUAL */
    private String source;

    /** 确认人 */
    private String confirmedBy;

    /** 确认时间 */
    private LocalDateTime confirmedAt;

    /** 关联错误发生次数 */
    private Integer occurrenceCount;

    /** 最近发生时间 */
    private LocalDateTime lastOccurredAt;

    /** 标签（JSON数组） */
    @TableField("tags")
    private String tagsJson;
}
