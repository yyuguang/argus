package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 应用级数据监控总配置。
 * <p>配置必须绑定到 ProjectMapping，不能作为脱离 appName / SCM 的全局监控项存在。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_data_monitor_config")
public class DataMonitorConfig extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 应用映射ID */
    private Long projectMappingId;

    /** SCM配置ID */
    private Long scmConfigId;

    /** 应用名称 */
    private String appName;

    /** 环境标识 */
    private String environment;

    /** 是否启用数据监控 */
    private Boolean enabled;

    /** 负责人团队 */
    private String ownerTeam;

    /** 技术负责人 */
    private String techOwner;

    /** 告警 Webhook 模式：SCM_CONFIG/CUSTOM */
    private String alertWebhookMode;

    /** 默认数据库运行态采集间隔秒数 */
    private Integer defaultRuntimeCollectIntervalSeconds;

    /** 默认连接池指标推送间隔秒数 */
    private Integer defaultPoolMetricPushIntervalSeconds;

    /** 默认接口日志质量巡检间隔秒数 */
    private Integer defaultLogQualityCheckIntervalSeconds;

    /** 告警扫描间隔秒数 */
    private Integer alertScanIntervalSeconds;

    /** 备注 */
    private String remark;
}
