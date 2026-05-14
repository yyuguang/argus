package com.lnzz.argus.datamonitor.service.impl;

import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.service.MysqlSlowSqlInspector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 JDBC 的 MySQL 5.7 慢 SQL 只读分析采集实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
public class JdbcMysql57SlowSqlInspector implements MysqlSlowSqlInspector {

    @Override
    public List<ExplainRow> explain(DataSourceConfig datasource, String password, String sqlText) {
        String safeSql = normalizeSingleStatement(sqlText);
        if (!StringUtils.hasText(safeSql)) {
            return List.of();
        }
        try (Connection connection = DriverManager.getConnection(
                datasource.getJdbcUrl(), datasource.getUsername(), password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("explain " + safeSql)) {
            List<ExplainRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new ExplainRow(
                        resultSet.getInt("id"),
                        resultSet.getString("select_type"),
                        resultSet.getString("table"),
                        resultSet.getString("type"),
                        resultSet.getString("possible_keys"),
                        resultSet.getString("key"),
                        resultSet.getLong("rows"),
                        resultSet.getString("Extra")
                ));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("EXPLAIN 执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TableInfo> queryTables(DataSourceConfig datasource, String password, Set<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return List.of();
        }
        String inClause = tableNames.stream()
                .map(this::quote)
                .collect(Collectors.joining(","));
        String sql = "select table_schema, table_name, table_rows, data_length, index_length "
                + "from information_schema.TABLES where table_schema = " + quote(datasource.getDatabaseName())
                + " and table_name in (" + inClause + ")";
        try (Connection connection = DriverManager.getConnection(
                datasource.getJdbcUrl(), datasource.getUsername(), password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<TableInfo> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new TableInfo(
                        resultSet.getString("table_schema"),
                        resultSet.getString("table_name"),
                        resultSet.getLong("table_rows"),
                        resultSet.getLong("data_length"),
                        resultSet.getLong("index_length")
                ));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("读取表规模失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<IndexInfo> queryIndexes(DataSourceConfig datasource, String password, Set<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return List.of();
        }
        String inClause = tableNames.stream()
                .map(this::quote)
                .collect(Collectors.joining(","));
        String sql = "select table_name, index_name, seq_in_index, column_name, cardinality, non_unique "
                + "from information_schema.STATISTICS where table_schema = " + quote(datasource.getDatabaseName())
                + " and table_name in (" + inClause + ") order by table_name, index_name, seq_in_index";
        try (Connection connection = DriverManager.getConnection(
                datasource.getJdbcUrl(), datasource.getUsername(), password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            List<IndexInfo> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new IndexInfo(
                        resultSet.getString("table_name"),
                        resultSet.getString("index_name"),
                        resultSet.getInt("seq_in_index"),
                        resultSet.getString("column_name"),
                        resultSet.getLong("cardinality"),
                        resultSet.getInt("non_unique") == 1
                ));
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("读取索引信息失败: " + e.getMessage(), e);
        }
    }

    private String normalizeSingleStatement(String sqlText) {
        if (!StringUtils.hasText(sqlText)) {
            return null;
        }
        String sql = sqlText.trim();
        while (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.contains(";")) {
            throw new IllegalArgumentException("慢 SQL 分析不支持多语句");
        }
        return sql;
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
