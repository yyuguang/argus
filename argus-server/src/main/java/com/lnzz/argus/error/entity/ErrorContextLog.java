package com.lnzz.argus.error.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 错误上下文日志快照实体（M4-B06）
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_error_context_log")
public class ErrorContextLog extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联错误事件ID */
    private Long errorEventId;

    /** 日志时间 */
    private LocalDateTime logTime;

    /** 日志级别 */
    private String logLevel;

    /** Logger 名称 */
    private String loggerName;

    /** 线程名 */
    private String threadName;

    /** 追踪ID */
    private String traceId;

    /** 日志内容 */
    private String message;

    /** 排序序号 */
    private Integer sortOrder;
}
