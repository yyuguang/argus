package com.lnzz.argus.error.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * Agent 推送批次记录实体
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_agent_push_batch")
public class AgentPushBatch extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 批次标识(UUID) */
    private String batchId;

    /** Agent 实例标识 */
    private String agentId;

    /** 推送条目总数 */
    private Integer entryCount;

    /** 接受数 */
    private Integer acceptedCount;

    /** 重复数 */
    private Integer duplicatedCount;

    /** 异常数 */
    private Integer errorCount;

    /** 状态: RECEIVED/PROCESSING/DONE */
    private String status;

    /** 接收时间 */
    private LocalDateTime receivedAt;
}
