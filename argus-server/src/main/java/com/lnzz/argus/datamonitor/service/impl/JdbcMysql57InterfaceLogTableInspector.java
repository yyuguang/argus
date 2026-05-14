package com.lnzz.argus.datamonitor.service.impl;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.InterfaceLogTableConfig;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 基于 JDBC 的 MySQL 5.7 接口日志表只读巡检实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
public class JdbcMysql57InterfaceLogTableInspector implements InterfaceLogTableInspector {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

    @Override
    public void validateMapping(DataSourceConfig datasource, String password, InterfaceLogTableConfig config) {
        requireIdentifier(config.getTableName(), "日志表名");
        Set<String> columns = mappedColumns(config);
        columns.forEach(column -> requireIdentifier(column, "字段名"));
        String columnInClause = columns.stream().map(this::quote).collect(Collectors.joining(","));
        String sql = "select column_name from information_schema.COLUMNS where table_schema = "
                + quote(datasource.getDatabaseName()) + " and table_name = " + quote(config.getTableName())
                + " and column_name in (" + columnInClause + ")";
        try (Connection connection = DriverManager.getConnection(datasource.getJdbcUrl(), datasource.getUsername(), password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            Set<String> existing = new LinkedHashSet<>();
            while (resultSet.next()) {
                existing.add(resultSet.getString("column_name"));
            }
            if (existing.size() != columns.size()) {
                Set<String> missing = new LinkedHashSet<>(columns);
                missing.removeAll(existing);
                throw new BizException(ResultCode.PARAM_ERROR, "日志表字段映射不存在或不完整: " + missing);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("校验接口日志表字段失败: " + e.getMessage(), e);
        }
    }

    @Override
    public LogTableScanMetrics scan(DataSourceConfig datasource,
                                    String password,
                                    InterfaceLogTableConfig config,
                                    LogQualityRules rules,
                                    ScanWindow window) {
        validateMapping(datasource, password, config);
        String where = buildWhere(config, window);
        try (Connection connection = DriverManager.getConnection(datasource.getJdbcUrl(), datasource.getUsername(), password);
             Statement statement = connection.createStatement()) {
            long total = queryLong(statement, "select count(*) from `" + config.getTableName() + "` " + where);
            String maxPk = queryString(statement, "select cast(max(`" + config.getPrimaryKeyColumn() + "`) as char) "
                    + "from `" + config.getTableName() + "` " + where);
            LocalDateTime latest = queryDateTime(statement, "select max(`" + config.getRequestTimeColumn() + "`) "
                    + "from `" + config.getTableName() + "`");
            long nullRequired = queryNullRequired(statement, config, rules, where);
            long duplicateRequestId = duplicateCount(statement, config.getTableName(), config.getRequestIdColumn(), where);
            long duplicateTraceId = duplicateCount(statement, config.getTableName(), config.getTraceIdColumn(), where);
            long invalidTime = queryLong(statement, "select count(*) from `" + config.getTableName() + "` " + where
                    + and(where) + "`" + config.getResponseTimeColumn() + "` < `" + config.getRequestTimeColumn() + "`");
            long invalidStatus = invalidStatusCount(statement, config, rules, where);
            long emptyResponse = queryLong(statement, "select count(*) from `" + config.getTableName() + "` " + where
                    + and(where) + "(`" + config.getResponseBodyColumn() + "` is null or length(`"
                    + config.getResponseBodyColumn() + "`) = 0)");
            long oversizeResponse = rules.maxResponseBodyKb() == null ? 0L
                    : queryLong(statement, "select count(*) from `" + config.getTableName() + "` " + where
                    + and(where) + "length(`" + config.getResponseBodyColumn() + "`) > "
                    + (rules.maxResponseBodyKb() * 1024L));
            TableSize tableSize = queryTableSize(statement, datasource.getDatabaseName(), config.getTableName());
            return new LogTableScanMetrics(total, maxPk, latest, nullRequired, duplicateRequestId, duplicateTraceId,
                    invalidTime, invalidStatus, 0L, emptyResponse, oversizeResponse, tableSize.rows(),
                    tableSize.dataLength(), tableSize.indexLength(), maxPk, "{\"table\":\"" + config.getTableName() + "\"}");
        } catch (SQLException e) {
            throw new IllegalStateException("接口日志表质量巡检失败: " + e.getMessage(), e);
        }
    }

    private String buildWhere(InterfaceLogTableConfig config, ScanWindow window) {
        if ("ID_INCREMENT".equalsIgnoreCase(window.scanMode()) && StringUtils.hasText(window.lastScanValue())) {
            return "where `" + config.getPrimaryKeyColumn() + "` > " + quote(window.lastScanValue());
        }
        if ("TIME_WINDOW".equalsIgnoreCase(window.scanMode()) && window.windowStart() != null && window.windowEnd() != null) {
            return "where `" + config.getRequestTimeColumn() + "` >= " + quote(window.windowStart().toString())
                    + " and `" + config.getRequestTimeColumn() + "` < " + quote(window.windowEnd().toString());
        }
        return "";
    }

    private long queryNullRequired(Statement statement,
                                   InterfaceLogTableConfig config,
                                   LogQualityRules rules,
                                   String where) throws SQLException {
        Set<String> required = rules.requiredColumns() == null || rules.requiredColumns().isEmpty()
                ? Set.of(config.getPrimaryKeyColumn(), config.getInterfaceCodeColumn(), config.getRequestTimeColumn(),
                config.getResponseTimeColumn(), config.getResponseBodyColumn())
                : rules.requiredColumns();
        String condition = required.stream()
                .filter(StringUtils::hasText)
                .map(column -> "(`" + column + "` is null or cast(`" + column + "` as char) = '')")
                .collect(Collectors.joining(" or "));
        if (!StringUtils.hasText(condition)) {
            return 0L;
        }
        return queryLong(statement, "select count(*) from `" + config.getTableName() + "` " + where
                + and(where) + "(" + condition + ")");
    }

    private long duplicateCount(Statement statement, String tableName, String column, String where) throws SQLException {
        if (!StringUtils.hasText(column)) {
            return 0L;
        }
        return queryLong(statement, "select count(*) from (select `" + column + "` from `" + tableName + "` "
                + where + and(where) + "`" + column + "` is not null and cast(`" + column + "` as char) <> '' "
                + "group by `" + column + "` having count(*) > 1) t");
    }

    private long invalidStatusCount(Statement statement,
                                    InterfaceLogTableConfig config,
                                    LogQualityRules rules,
                                    String where) throws SQLException {
        if (!StringUtils.hasText(config.getStatusCodeColumn())
                || rules.validStatusCodes() == null || rules.validStatusCodes().isEmpty()) {
            return 0L;
        }
        String codes = rules.validStatusCodes().stream().map(this::quote).collect(Collectors.joining(","));
        return queryLong(statement, "select count(*) from `" + config.getTableName() + "` " + where
                + and(where) + "cast(`" + config.getStatusCodeColumn() + "` as char) not in (" + codes + ")");
    }

    private TableSize queryTableSize(Statement statement, String schema, String tableName) throws SQLException {
        String sql = "select table_rows, data_length, index_length from information_schema.TABLES where table_schema = "
                + quote(schema) + " and table_name = " + quote(tableName);
        try (ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return new TableSize(rs.getLong("table_rows"), rs.getLong("data_length"), rs.getLong("index_length"));
            }
            return new TableSize(0L, 0L, 0L);
        }
    }

    private long queryLong(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private String queryString(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private LocalDateTime queryDateTime(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() && rs.getTimestamp(1) != null ? rs.getTimestamp(1).toLocalDateTime() : null;
        }
    }

    private String and(String where) {
        return StringUtils.hasText(where) ? " and " : " where ";
    }

    private Set<String> mappedColumns(InterfaceLogTableConfig config) {
        Set<String> columns = new LinkedHashSet<>();
        add(columns, config.getPrimaryKeyColumn());
        add(columns, config.getInterfaceCodeColumn());
        add(columns, config.getRequestTimeColumn());
        add(columns, config.getResponseTimeColumn());
        add(columns, config.getResponseBodyColumn());
        add(columns, config.getStatusCodeColumn());
        add(columns, config.getRequestIdColumn());
        add(columns, config.getTraceIdColumn());
        return columns;
    }

    private void add(Set<String> columns, String column) {
        if (StringUtils.hasText(column)) {
            columns.add(column);
        }
    }

    private void requireIdentifier(String value, String name) {
        if (!StringUtils.hasText(value) || !IDENTIFIER.matcher(value).matches()) {
            throw new BizException(ResultCode.PARAM_ERROR, name + "非法: " + value);
        }
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private record TableSize(long rows, long dataLength, long indexLength) {
    }
}
