package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 数据库锁等待与阻塞事件。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_db_lock_event")
public class DbLockEvent extends BaseEntity {

    /** 数据源ID */
    private Long datasourceId;

    /** 应用名称 */
    private String appName;

    /** 环境 */
    private String environment;

    /** 等待事务ID */
    private String waitingTrxId;

    /** 阻塞事务ID */
    private String blockingTrxId;

    /** 等待线程 */
    private Long waitingProcessId;

    /** 阻塞线程 */
    private Long blockingProcessId;

    /** 锁表 */
    private String lockTable;

    /** 锁索引 */
    private String lockIndex;

    /** 锁类型 */
    private String lockType;

    /** 等待时长 */
    private Integer waitSeconds;

    /** 等待 SQL */
    private String waitingSql;

    /** 阻塞 SQL */
    private String blockingSql;

    /** 事件指纹 */
    private String eventFingerprint;

    /** 风险等级 */
    private String riskLevel;

    /** 状态 */
    private String status;

    /** 发生时间 */
    private LocalDateTime occurredAt;
}
