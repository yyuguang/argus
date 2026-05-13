package com.lnzz.argus.datamonitor.service;

/**
 * 数据源只读连通性测试。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface DataSourceConnectivityTester {

    DataSourceTestResult test(DataSourceConnectionRequest request);

    record DataSourceConnectionRequest(
            String jdbcUrl,
            String username,
            String password
    ) {
    }

    record DataSourceTestResult(
            boolean connected,
            boolean readonlyVerified,
            boolean canExplain,
            boolean canReadProcesslist,
            boolean canReadInnodbStatus,
            String mysqlVersion,
            String message
    ) {
    }
}
