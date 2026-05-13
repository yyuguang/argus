package com.lnzz.argus.error.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 错误分析任务实体。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_error_analysis_task")
public class ErrorAnalysisTask extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 错误事件ID */
    private Long errorEventId;

    /** 触发类型: AUTO/MANUAL/MANUAL_RETRY */
    private String triggerType;

    /** 任务状态: PENDING/RUNNING/DONE/FAILED/TIMEOUT/SKIPPED */
    private String status;

    /** 关联分析结果ID */
    private Long analysisId;

    /** AI模型 */
    private String aiModel;

    /** 失败原因 */
    private String errorMessage;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;

    /** 执行耗时(ms) */
    private Long durationMs;
}
