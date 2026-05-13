package com.lnzz.argus.datamonitor.service;

import com.lnzz.argus.datamonitor.entity.DataSourceConfig;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * MySQL 5.7 运行现场只读采集客户端。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface MysqlRuntimeCollector {

    RuntimeSnapshot collect(DataSourceConfig datasource, String password);

    record RuntimeSnapshot(
            Map<String, Long> globalStatus,
            List<ProcessRow> processRows,
            List<InnodbTransactionRow> innodbTransactions,
            List<InnodbLockWaitRow> innodbLockWaits,
            List<InnodbLockRow> innodbLocks,
            LocalDateTime collectedAt
    ) {
    }

    record ProcessRow(
            Long id,
            String user,
            String host,
            String db,
            String command,
            Integer time,
            String state,
            String info
    ) {
    }

    record InnodbTransactionRow(
            String trxId,
            Long mysqlThreadId,
            Integer trxStartedSeconds,
            String trxState,
            String trxQuery
    ) {
    }

    record InnodbLockWaitRow(
            String requestingTrxId,
            String blockingTrxId,
            String requestedLockId,
            String blockingLockId
    ) {
    }

    record InnodbLockRow(
            String lockId,
            String lockTrxId,
            String lockMode,
            String lockType,
            String lockTable,
            String lockIndex
    ) {
    }
}
