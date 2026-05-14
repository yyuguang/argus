package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 数据库运行指标快照。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_db_metric_snapshot")
public class DbMetricSnapshot extends BaseEntity {

    /** 数据源ID */
    private Long datasourceId;

    /** 应用名称 */
    private String appName;

    /** 环境标识 */
    private String environment;

    /** 当前连接数 */
    private Integer threadsConnected;

    /** 活跃线程数 */
    private Integer threadsRunning;

    /** 最大连接数 */
    private Integer maxConnections;

    /** Questions 计数 */
    private Long questions;

    /** Select 计数 */
    private Long comSelect;

    /** Insert 计数 */
    private Long comInsert;

    /** Update 计数 */
    private Long comUpdate;

    /** Delete 计数 */
    private Long comDelete;

    /** 计算后的 QPS */
    private BigDecimal qps;

    /** 慢查询累计数 */
    private Long slowQueries;

    /** InnoDB 活跃事务数 */
    private Integer innodbTrxCount;

    /** InnoDB 锁等待数 */
    private Integer innodbLockWaitCount;

    /** 采集时间 */
    private LocalDateTime collectedAt;
}
