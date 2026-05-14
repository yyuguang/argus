package com.lnzz.argus.datamonitor.service.impl;

import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.service.MysqlRuntimeCollector;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 JDBC 的 MySQL 5.7 只读采集实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
public class JdbcMysql57RuntimeCollector implements MysqlRuntimeCollector {

    @Override
    public RuntimeSnapshot collect(DataSourceConfig datasource, String password) {
        try (Connection connection = DriverManager.getConnection(
                datasource.getJdbcUrl(), datasource.getUsername(), password)) {
            LocalDateTime collectedAt = LocalDateTime.now();
            Map<String, Long> globalStatus = Boolean.TRUE.equals(datasource.getCollectGlobalStatus())
                    ? queryGlobalStatus(connection)
                    : Map.of();
            List<ProcessRow> processRows = Boolean.TRUE.equals(datasource.getCollectProcesslist())
                    ? queryProcesslist(connection)
                    : List.of();
            List<InnodbTransactionRow> transactions = Boolean.TRUE.equals(datasource.getCollectInnodbTrx())
                    ? queryInnodbTransactions(connection, collectedAt)
                    : List.of();
            List<InnodbLockWaitRow> lockWaits = Boolean.TRUE.equals(datasource.getCollectInnodbLock())
                    ? queryInnodbLockWaits(connection)
                    : List.of();
            List<InnodbLockRow> locks = Boolean.TRUE.equals(datasource.getCollectInnodbLock())
                    ? queryInnodbLocks(connection)
                    : List.of();
            return new RuntimeSnapshot(globalStatus, processRows, transactions, lockWaits, locks, collectedAt);
        } catch (SQLException e) {
            throw new IllegalStateException("MySQL 运行现场采集失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Long> queryGlobalStatus(Connection connection) throws SQLException {
        Map<String, Long> status = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("show global status")) {
            while (resultSet.next()) {
                String name = resultSet.getString("Variable_name");
                String value = resultSet.getString("Value");
                status.put(name, parseLong(value));
            }
        }
        return status;
    }

    private List<ProcessRow> queryProcesslist(Connection connection) throws SQLException {
        List<ProcessRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select ID, USER, HOST, DB, COMMAND, TIME, STATE, INFO from information_schema.PROCESSLIST")) {
            while (resultSet.next()) {
                rows.add(new ProcessRow(
                        resultSet.getLong("ID"),
                        resultSet.getString("USER"),
                        resultSet.getString("HOST"),
                        resultSet.getString("DB"),
                        resultSet.getString("COMMAND"),
                        resultSet.getInt("TIME"),
                        resultSet.getString("STATE"),
                        resultSet.getString("INFO")
                ));
            }
        }
        return rows;
    }

    private List<InnodbTransactionRow> queryInnodbTransactions(Connection connection,
                                                              LocalDateTime collectedAt) throws SQLException {
        List<InnodbTransactionRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select trx_id, trx_mysql_thread_id, trx_started, trx_state, trx_query "
                             + "from information_schema.INNODB_TRX")) {
            while (resultSet.next()) {
                LocalDateTime started = resultSet.getTimestamp("trx_started") == null
                        ? null
                        : resultSet.getTimestamp("trx_started").toLocalDateTime();
                Integer durationSeconds = started == null
                        ? null
                        : Math.toIntExact(Math.max(0, Duration.between(started, collectedAt).getSeconds()));
                rows.add(new InnodbTransactionRow(
                        resultSet.getString("trx_id"),
                        resultSet.getLong("trx_mysql_thread_id"),
                        durationSeconds,
                        resultSet.getString("trx_state"),
                        resultSet.getString("trx_query")
                ));
            }
        }
        return rows;
    }

    private List<InnodbLockWaitRow> queryInnodbLockWaits(Connection connection) throws SQLException {
        List<InnodbLockWaitRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select requesting_trx_id, blocking_trx_id, requested_lock_id, blocking_lock_id "
                             + "from information_schema.INNODB_LOCK_WAITS")) {
            while (resultSet.next()) {
                rows.add(new InnodbLockWaitRow(
                        resultSet.getString("requesting_trx_id"),
                        resultSet.getString("blocking_trx_id"),
                        resultSet.getString("requested_lock_id"),
                        resultSet.getString("blocking_lock_id")
                ));
            }
        }
        return rows;
    }

    private List<InnodbLockRow> queryInnodbLocks(Connection connection) throws SQLException {
        List<InnodbLockRow> rows = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "select lock_id, lock_trx_id, lock_mode, lock_type, lock_table, lock_index "
                             + "from information_schema.INNODB_LOCKS")) {
            while (resultSet.next()) {
                rows.add(new InnodbLockRow(
                        resultSet.getString("lock_id"),
                        resultSet.getString("lock_trx_id"),
                        resultSet.getString("lock_mode"),
                        resultSet.getString("lock_type"),
                        resultSet.getString("lock_table"),
                        resultSet.getString("lock_index")
                ));
            }
        }
        return rows;
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }
}
