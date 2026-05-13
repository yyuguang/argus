package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 接口日志表质量巡检配置。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_interface_log_table_config")
public class InterfaceLogTableConfig extends BaseEntity {

    private Long monitorConfigId;
    private Long projectMappingId;
    private Long datasourceId;
    private String appName;
    private String environment;
    private String configName;
    private String tableName;
    private String primaryKeyColumn;
    private String interfaceCodeColumn;
    private String requestTimeColumn;
    private String responseTimeColumn;
    private String responseBodyColumn;
    private String statusCodeColumn;
    private String requestIdColumn;
    private String traceIdColumn;
    private String scanMode;
    private String lastScanValue;
    private String qualityRules;
    private String alertRules;
    private Boolean enabled;
}
