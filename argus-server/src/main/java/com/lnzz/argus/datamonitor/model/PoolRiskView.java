package com.lnzz.argus.datamonitor.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 连接池风险工作台视图对象。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class PoolRiskView {

    private Long id;
    private String appName;
    private String environment;
    private String datasourceName;
    private String poolName;
    private String poolType;
    private Integer activeConnections;
    private Integer maxConnections;
    private Integer waitingThreads;
    private Long timeoutCount;
    private Double usagePercent;
    private String riskLevel;
    private String riskType;
    private String riskReason;
    private LocalDateTime collectedAt;
}
