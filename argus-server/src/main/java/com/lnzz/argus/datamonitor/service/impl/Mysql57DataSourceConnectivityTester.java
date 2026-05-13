package com.lnzz.argus.datamonitor.service.impl;

import com.lnzz.argus.datamonitor.service.DataSourceConnectivityTester;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * MySQL 5.7 只读监控账号连通性测试。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
public class Mysql57DataSourceConnectivityTester implements DataSourceConnectivityTester {

    @Override
    public DataSourceTestResult test(DataSourceConnectionRequest request) {
        try (Connection connection = DriverManager.getConnection(
                request.jdbcUrl(), request.username(), request.password())) {
            String version = querySingle(connection, "select version()");
            boolean canExplain = execute(connection, "explain select 1");
            boolean canReadProcesslist = execute(connection, "show full processlist");
            boolean canReadInnodbStatus = execute(connection,
                    "select trx_id from information_schema.innodb_trx limit 1")
                    && execute(connection,
                    "select requesting_trx_id from information_schema.innodb_lock_waits limit 1");
            boolean readonly = verifyReadOnlyGrants(connection);
            String message = readonly ? "只读监控权限验证通过" : "账号存在写权限或授权范围过宽";
            return new DataSourceTestResult(true, readonly, canExplain, canReadProcesslist,
                    canReadInnodbStatus, version, message);
        } catch (SQLException e) {
            return new DataSourceTestResult(false, false, false, false,
                    false, null, e.getMessage());
        }
    }

    private String querySingle(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private boolean execute(Connection connection, String sql) {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private boolean verifyReadOnlyGrants(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("show grants for current_user")) {
            while (resultSet.next()) {
                String grant = resultSet.getString(1);
                if (hasWritePrivilege(grant)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean hasWritePrivilege(String grant) {
        if (!StringUtils.hasText(grant)) {
            return false;
        }
        String normalized = grant.toUpperCase(Locale.ROOT);
        String[] denied = {
                "ALL PRIVILEGES",
                "INSERT",
                "UPDATE",
                "DELETE",
                "CREATE",
                "DROP",
                "ALTER",
                "INDEX",
                "TRIGGER",
                "EVENT",
                "EXECUTE",
                "LOCK TABLES",
                "CREATE TEMPORARY TABLES",
                "CREATE ROUTINE",
                "ALTER ROUTINE",
                "CREATE VIEW"
        };
        for (String privilege : denied) {
            if (normalized.contains(privilege)) {
                return true;
            }
        }
        return false;
    }
}
