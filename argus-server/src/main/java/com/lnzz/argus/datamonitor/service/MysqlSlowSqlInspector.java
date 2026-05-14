package com.lnzz.argus.datamonitor.service;

import com.lnzz.argus.datamonitor.entity.DataSourceConfig;

import java.util.List;
import java.util.Set;

/**
 * MySQL 5.7 慢 SQL 只读分析采集器。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface MysqlSlowSqlInspector {

    List<ExplainRow> explain(DataSourceConfig datasource, String password, String sqlText);

    List<TableInfo> queryTables(DataSourceConfig datasource, String password, Set<String> tableNames);

    List<IndexInfo> queryIndexes(DataSourceConfig datasource, String password, Set<String> tableNames);

    record ExplainRow(
            Integer id,
            String selectType,
            String tableName,
            String accessType,
            String possibleKeys,
            String keyName,
            Long rows,
            String extra
    ) {
    }

    record TableInfo(
            String tableSchema,
            String tableName,
            Long tableRows,
            Long dataLength,
            Long indexLength
    ) {
    }

    record IndexInfo(
            String tableName,
            String indexName,
            Integer seqInIndex,
            String columnName,
            Long cardinality,
            Boolean nonUnique
    ) {
    }
}
