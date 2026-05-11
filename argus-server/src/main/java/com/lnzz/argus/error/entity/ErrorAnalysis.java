package com.lnzz.argus.error.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.math.BigDecimal;

/**
 * AI 错误分析结果实体
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_error_analysis")
public class ErrorAnalysis extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联错误事件ID */
    private Long errorEventId;

    /** AI 分析根因 */
    private String rootCause;

    /** 技术细节 */
    private String technicalDetail;

    /** 影响范围 */
    private String impactScope;

    /** AI 校准后严重度 */
    private String finalSeverity;

    /** 修复描述 */
    private String fixDescription;

    /** 修复代码示例 */
    private String fixCodeExample;

    /** 需修改的文件 */
    private String fixFilePath;

    /** 修改行范围 */
    private String fixLineRange;

    /** 预估工作量 */
    private String estimatedEffort;

    /** 预防建议 */
    private String preventionAdvice;

    /** AI 置信度(0-1) */
    private BigDecimal confidence;

    /** 消耗 Token 数 */
    private Integer tokensUsed;

    /** 分析耗时(ms) */
    private Long duration;

    /** 使用的模型 */
    private String aiModel;

    /** 分析来源: AI/MANUAL/HYBRID */
    private String source;

    /** 人工补充结论 */
    private String manualConclusion;
}
