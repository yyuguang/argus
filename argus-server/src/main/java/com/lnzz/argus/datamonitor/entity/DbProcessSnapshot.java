package com.lnzz.argus.datamonitor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 当前执行 SQL 快照。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_db_process_snapshot")
public class DbProcessSnapshot extends BaseEntity {

    /** 数据源ID */
    private Long datasourceId;

    /** MySQL 线程ID */
    private Long mysqlProcessId;

    /** 执行用户 */
    private String userName;

    /** 来源 host */
    private String hostInfo;

    /** 数据库名 */
    private String databaseName;

    /** Command */
    private String commandType;

    /** State */
    private String processState;

    /** 已执行秒数 */
    private Integer durationSeconds;

    /** SQL 指纹 */
    private String sqlFingerprint;

    /** 完整 SQL */
    private String sqlText;

    /** 脱敏 SQL */
    private String sqlTextMasked;

    /** 风险类型 */
    private String riskType;

    /** 风险等级 */
    private String riskLevel;

    /** 采集时间 */
    private LocalDateTime collectedAt;
}
