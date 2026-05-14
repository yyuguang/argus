package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 应用级业务库只读数据源配置。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_data_source_config")
public class DataSourceConfig extends BaseEntity {

    /** 数据监控总配置ID */
    private Long monitorConfigId;

    /** 应用映射ID */
    private Long projectMappingId;

    /** 数据源编码 */
    private String datasourceCode;

    /** 数据源名称 */
    private String datasourceName;

    /** 数据库类型，首版固定 MYSQL */
    private String dbType;

    /** 数据库版本，首版固定 5.7 */
    private String dbVersion;

    /** JDBC地址 */
    private String jdbcUrl;

    /** 主机 */
    private String host;

    /** 端口 */
    private Integer port;

    /** 数据库名 */
    private String databaseName;

    /** 只读账号 */
    private String username;

    /** 加密后的密码或密钥引用 */
    private String passwordSecret;

    /** 是否只读 */
    private Boolean readonly;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否采集 processlist */
    private Boolean collectProcesslist;

    /** 是否采集 InnoDB 事务 */
    private Boolean collectInnodbTrx;

    /** 是否采集 InnoDB 锁等待 */
    private Boolean collectInnodbLock;

    /** 是否采集 global status */
    private Boolean collectGlobalStatus;

    /** 是否允许 Explain */
    private Boolean explainEnabled;

    /** 是否采集完整 SQL */
    private Boolean fullSqlCollectEnabled;

    /** 数据库运行态采集间隔秒数 */
    private Integer runtimeCollectIntervalSeconds;

    /** 连接池指标期望推送间隔秒数 */
    private Integer poolMetricPushIntervalSeconds;

    /** 阈值配置JSON */
    private String thresholdConfig;
}
