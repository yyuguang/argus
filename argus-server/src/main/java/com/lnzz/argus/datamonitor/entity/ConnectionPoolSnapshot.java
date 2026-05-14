package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 连接池指标快照。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_connection_pool_snapshot")
public class ConnectionPoolSnapshot extends BaseEntity {

    /** 应用监控配置ID */
    private Long monitorConfigId;

    /** 数据源ID */
    private Long datasourceId;

    /** 应用名称 */
    private String appName;

    /** 环境 */
    private String environment;

    /** 数据源名称 */
    private String datasourceName;

    /** 连接池类型：HIKARI/DRUID */
    private String poolType;

    /** 活跃连接 */
    private Integer activeConnections;

    /** 空闲连接 */
    private Integer idleConnections;

    /** 最大连接 */
    private Integer maxConnections;

    /** 等待线程 */
    private Integer waitingThreads;

    /** 平均获取耗时 */
    private Long connectionAcquireAvgMs;

    /** 最大获取耗时 */
    private Long connectionAcquireMaxMs;

    /** 超时次数 */
    private Long timeoutCount;

    /** 错误次数 */
    private Long errorCount;

    /** 风险类型 */
    private String riskType;

    /** 风险等级 */
    private String riskLevel;

    /** 采集时间 */
    private LocalDateTime collectedAt;
}
