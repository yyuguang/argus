package com.lnzz.argus.common.constant;

import java.util.Set;

/**
 * 数据监控领域常量。
 *
 * @author lnzz
 * @since 1.0.0
 */
public final class DataMonitorConstants {

    /** 告警 Webhook 跟随 SCM 配置。 */
    public static final String ALERT_WEBHOOK_MODE_SCM_CONFIG = "SCM_CONFIG";

    /** MySQL 数据库类型编码。 */
    public static final String DB_TYPE_MYSQL = "MYSQL";

    /** MySQL 5.7 版本号前缀。 */
    public static final String DB_VERSION_MYSQL_57_PREFIX = "5.7";

    /** MySQL JDBC URL 前缀。 */
    public static final String MYSQL_JDBC_PREFIX = "jdbc:mysql://";

    /** MySQL 默认端口。 */
    public static final int DEFAULT_MYSQL_PORT = 3306;

    /** 默认数据库运行现场采集间隔，单位秒。 */
    public static final int DEFAULT_RUNTIME_COLLECT_INTERVAL_SECONDS = 30;

    /** 默认连接池指标推送间隔，单位秒。 */
    public static final int DEFAULT_POOL_METRIC_PUSH_INTERVAL_SECONDS = 30;

    /** 默认日志质量巡检间隔，单位秒。 */
    public static final int DEFAULT_LOG_QUALITY_CHECK_INTERVAL_SECONDS = 300;

    /** 默认告警扫描间隔，单位秒。 */
    public static final int DEFAULT_ALERT_SCAN_INTERVAL_SECONDS = 60;

    /** 按自增 ID 扫描接口日志表。 */
    public static final String SCAN_MODE_ID_INCREMENT = "ID_INCREMENT";

    /** 按时间窗口扫描接口日志表。 */
    public static final String SCAN_MODE_TIME_WINDOW = "TIME_WINDOW";

    /** 支持的接口日志表扫描模式集合。 */
    public static final Set<String> SUPPORTED_SCAN_MODES = Set.of(SCAN_MODE_ID_INCREMENT, SCAN_MODE_TIME_WINDOW);

    /** 慢 SQL 来源类型：slow log。 */
    public static final String SLOW_LOG_SOURCE_TYPE = "SLOW_LOG";

    /** 慢 SQL 分析状态：待处理。 */
    public static final String ANALYSIS_STATUS_PENDING = "PENDING";

    /** 慢 SQL 分析状态：已忽略。 */
    public static final String ANALYSIS_STATUS_IGNORED = "IGNORED";

    /** 慢 SQL 分析状态：已确认。 */
    public static final String ANALYSIS_STATUS_CONFIRMED = "CONFIRMED";

    /** 人工处理动作：忽略。 */
    public static final String ACTION_IGNORE = "IGNORE";

    /** 慢 SQL 风险：锁等待。 */
    public static final String RISK_LOCK_WAIT = "LOCK_WAIT";

    /** 慢 SQL 风险：分页方式不合理。 */
    public static final String RISK_BAD_PAGINATION = "BAD_PAGINATION";

    /** 慢 SQL 风险：长事务。 */
    public static final String RISK_LONG_TRANSACTION = "LONG_TRANSACTION";

    /** 连接池风险：连接池耗尽。 */
    public static final String RISK_POOL_EXHAUSTED = "POOL_EXHAUSTED";

    /** 连接池风险：连接池使用率过高。 */
    public static final String RISK_POOL_HIGH_USAGE = "POOL_HIGH_USAGE";

    /** 连接池风险：获取连接耗时过长。 */
    public static final String RISK_POOL_ACQUIRE_SLOW = "POOL_ACQUIRE_SLOW";

    /** 连接池风险：连接池上报异常。 */
    public static final String RISK_POOL_ERROR = "POOL_ERROR";

    /** 日志质量问题：必填字段为空。 */
    public static final String ISSUE_REQUIRED_FIELD_EMPTY = "REQUIRED_FIELD_EMPTY";

    /** 日志质量问题：扫描窗口内无新数据。 */
    public static final String ISSUE_NO_NEW_DATA = "NO_NEW_DATA";

    /** 日志质量问题：主键或业务唯一键重复。 */
    public static final String ISSUE_DUPLICATE_ID = "DUPLICATE_ID";

    /** 日志质量问题：时间字段非法。 */
    public static final String ISSUE_INVALID_TIME = "INVALID_TIME";

    /** 日志质量问题：状态字段非法。 */
    public static final String ISSUE_INVALID_STATUS = "INVALID_STATUS";

    /** 日志质量问题：状态流转冲突。 */
    public static final String ISSUE_STATUS_CONFLICT = "STATUS_CONFLICT";

    /** 日志质量问题：响应体存在敏感或异常风险。 */
    public static final String ISSUE_RESPONSE_BODY_RISK = "RESPONSE_BODY_RISK";

    /** 日志质量问题：数据增长异常。 */
    public static final String ISSUE_GROWTH_RISK = "GROWTH_RISK";

    private DataMonitorConstants() {
    }
}
