package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * MySQL slow log 接入配置。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_slow_log_config")
public class SlowLogConfig extends BaseEntity {

    /** 数据源ID */
    private Long datasourceId;

    /** 是否启用 */
    private Boolean enabled;

    /** 来源类型：FILE_TAIL/PUSH/TABLE */
    private String sourceType;

    /** slow log 文件路径 */
    private String logPath;

    /** 字符集 */
    private String charset;

    /** 最小采集耗时 */
    private Long minQueryTimeMs;

    /** 是否采集完整 SQL */
    private Boolean collectFullSql;

    /** slow log 采集间隔秒数 */
    private Integer collectIntervalSeconds;

    /** 文件读取位点 */
    private Long cursorOffset;

    /** 最近采集时间 */
    private LocalDateTime lastCollectedAt;
}
