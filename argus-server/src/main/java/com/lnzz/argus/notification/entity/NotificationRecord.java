package com.lnzz.argus.notification.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 通知记录实体
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_notification_record")
public class NotificationRecord extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 类型: REVIEW/ERROR_ALERT/REPORT */
    private String type;

    /** 渠道 */
    private String channel;

    /** 关联业务ID */
    private Long refId;

    /** 关联类型 */
    private String refType;

    /** 通知内容摘要 */
    private String contentSummary;

    /** 状态: PENDING/SENT/FAILED/SKIPPED */
    private String status;

    /** 失败原因 */
    private String errorMessage;

    /** 重试次数 */
    private Integer retryCount;

    /** 发送时间 */
    private LocalDateTime sentAt;
}
