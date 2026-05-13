package com.lnzz.argus.datamonitor.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.datamonitor.entity.ConnectionPoolSnapshot;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.DbLockEvent;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.ConnectionPoolSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DbLockEventMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.DataSourceSecretCodec;
import com.lnzz.argus.datamonitor.service.MysqlSlowSqlInspector;
import com.lnzz.argus.datamonitor.service.MysqlSlowSqlInspector.ExplainRow;
import com.lnzz.argus.datamonitor.service.MysqlSlowSqlInspector.IndexInfo;
import com.lnzz.argus.datamonitor.service.MysqlSlowSqlInspector.TableInfo;
import com.lnzz.argus.datamonitor.service.SlowSqlAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 慢 SQL 根因分析服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class SlowSqlAnalysisServiceImpl implements SlowSqlAnalysisService {

    private static final int DEFAULT_LIMIT = 20;
    private static final Pattern TABLE_PATTERN = Pattern.compile(
            "(?i)\\b(?:from|join|update|into)\\s+`?([a-zA-Z0-9_]+)`?");
    private static final Pattern WHERE_COLUMN_PATTERN = Pattern.compile(
            "(?i)\\bwhere\\b(.+?)(?:\\border\\s+by\\b|\\bgroup\\s+by\\b|\\blimit\\b|$)");
    private static final Pattern CONDITION_COLUMN_PATTERN = Pattern.compile("`?([a-zA-Z0-9_]+)`?\\s*(?:=|>|<|>=|<=|in\\b|like\\b)");

    private final SlowSqlEventMapper slowSqlEventMapper;
    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final DbLockEventMapper dbLockEventMapper;
    private final ConnectionPoolSnapshotMapper connectionPoolSnapshotMapper;
    private final DataSourceSecretCodec secretCodec;
    private final MysqlSlowSqlInspector slowSqlInspector;

    @Override
    public SlowSqlAnalysisResult analyzeEvent(Long eventId) {
        SlowSqlEvent event = slowSqlEventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "慢 SQL 事件不存在: " + eventId);
        }
        DataSourceConfig datasource = dataSourceConfigMapper.selectById(event.getDatasourceId());
        if (datasource == null) {
            throw new BizException(ResultCode.NOT_FOUND, "慢 SQL 关联数据源不存在: " + event.getDatasourceId());
        }
        return analyze(event, datasource);
    }

    @Override
    public List<SlowSqlAnalysisResult> analyzePending(Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, 100);
        List<SlowSqlEvent> events = slowSqlEventMapper.selectList(new LambdaQueryWrapper<SlowSqlEvent>()
                .eq(SlowSqlEvent::getAnalysisStatus, "PENDING")
                .orderByAsc(SlowSqlEvent::getOccurredAt)
                .last("limit " + safeLimit));
        return events.stream()
                .map(event -> {
                    DataSourceConfig datasource = dataSourceConfigMapper.selectById(event.getDatasourceId());
                    if (datasource == null) {
                        markFailed(event, "关联数据源不存在");
                        return result(event, "关联数据源不存在");
                    }
                    return analyze(event, datasource);
                })
                .toList();
    }

    private SlowSqlAnalysisResult analyze(SlowSqlEvent event, DataSourceConfig datasource) {
        String sqlText = StringUtils.hasText(event.getSqlText())
                ? event.getSqlText()
                : event.getSqlTextMasked();
        if (!StringUtils.hasText(sqlText)) {
            markFailed(event, "缺少 SQL 文本，无法分析");
            return result(event, "缺少 SQL 文本，无法分析");
        }
        try {
            String password = secretCodec.decrypt(datasource.getPasswordSecret());
            Set<String> tableNames = extractTableNames(sqlText);
            List<ExplainRow> explainRows = Boolean.TRUE.equals(datasource.getExplainEnabled())
                    ? slowSqlInspector.explain(datasource, password, sqlText)
                    : List.of();
            if (tableNames.isEmpty()) {
                explainRows.stream()
                        .map(ExplainRow::tableName)
                        .filter(StringUtils::hasText)
                        .forEach(tableNames::add);
            }
            List<TableInfo> tableInfos = slowSqlInspector.queryTables(datasource, password, tableNames);
            List<IndexInfo> indexInfos = slowSqlInspector.queryIndexes(datasource, password, tableNames);
            AnalysisDecision decision = decide(event, sqlText, explainRows, tableInfos, indexInfos);

            event.setExplainJson(JSON.toJSONString(explainRows));
            event.setTableInfoJson(JSON.toJSONString(tableInfos));
            event.setIndexInfoJson(JSON.toJSONString(indexInfos));
            event.setCauseType(decision.causeType());
            event.setRiskLevel(decision.riskLevel());
            event.setAnalysisStatus("DONE");
            event.setRootCause(decision.rootCause());
            event.setOptimizationSuggestion(decision.optimizationSuggestion());
            event.setIndexSuggestionSql(decision.indexSuggestionSql());
            event.setNeedDba(decision.needDba());
            event.setNeedDeveloper(true);
            event.setConfidence(decision.confidence());
            slowSqlEventMapper.updateById(event);
            return result(event, "分析完成");
        } catch (Exception e) {
            markFailed(event, e.getMessage());
            return result(event, e.getMessage());
        }
    }

    private AnalysisDecision decide(SlowSqlEvent event,
                                    String sqlText,
                                    List<ExplainRow> explainRows,
                                    List<TableInfo> tableInfos,
                                    List<IndexInfo> indexInfos) {
        DbLockEvent lockEvent = event.getRelatedLockEventId() == null ? null
                : dbLockEventMapper.selectById(event.getRelatedLockEventId());
        ConnectionPoolSnapshot poolSnapshot = event.getRelatedPoolSnapshotId() == null ? null
                : connectionPoolSnapshotMapper.selectById(event.getRelatedPoolSnapshotId());
        String lowerSql = sqlText.toLowerCase(Locale.ROOT);

        if (lockEvent != null || containsLockState(event)) {
            return new AnalysisDecision("LOCK_WAIT", "P1",
                    "慢 SQL 与锁等待事件或锁状态同时出现，当前耗时主要受锁等待/阻塞链影响。",
                    "优先排查阻塞事务、长事务和相同表上的写操作；确认业务是否存在大事务或未及时提交。",
                    null, true, BigDecimal.valueOf(0.88));
        }
        if (poolSnapshot != null && StringUtils.hasText(poolSnapshot.getRiskType())) {
            String level = "POOL_EXHAUSTED".equals(poolSnapshot.getRiskType()) ? "P1" : "P2";
            return new AnalysisDecision("POOL_EXHAUSTED", level,
                    "慢 SQL 发生窗口内连接池存在 " + poolSnapshot.getRiskType() + " 风险，业务线程可能被连接获取或池耗尽放大。",
                    "结合连接池最大连接数、等待线程、超时次数和应用侧调用链路排查；数据库 SQL 优化与连接池容量需要同时评估。",
                    null, false, BigDecimal.valueOf(0.82));
        }
        ExplainRow worstExplain = explainRows.stream()
                .max(Comparator.comparing(row -> row.rows() == null ? 0L : row.rows()))
                .orElse(null);
        long maxExplainRows = worstExplain == null || worstExplain.rows() == null ? 0L : worstExplain.rows();
        if (worstExplain != null && "ALL".equalsIgnoreCase(worstExplain.accessType())
                && !StringUtils.hasText(worstExplain.possibleKeys())) {
            return new AnalysisDecision("MISSING_INDEX", riskByRows(event, maxExplainRows),
                    "EXPLAIN 显示访问类型为 ALL 且没有可用索引，疑似缺少匹配查询条件的索引。",
                    "为高频过滤条件补充联合索引，并确认字段选择性；上线前必须由 DBA 评估执行计划和写入成本。",
                    buildIndexSuggestion(sqlText, worstExplain.tableName(), indexInfos), true, BigDecimal.valueOf(0.86));
        }
        if (worstExplain != null && !StringUtils.hasText(worstExplain.keyName())
                && StringUtils.hasText(worstExplain.possibleKeys())) {
            return new AnalysisDecision("INDEX_NOT_USED", riskByRows(event, maxExplainRows),
                    "EXPLAIN 存在候选索引但实际未使用，可能存在函数包裹、隐式类型转换、条件选择性差或查询写法问题。",
                    "检查 WHERE 条件字段类型、函数调用、排序字段和联合索引最左前缀；优先改写 SQL，再评估索引调整。",
                    null, true, BigDecimal.valueOf(0.78));
        }
        if (isLargeScan(event, tableInfos, maxExplainRows)) {
            return new AnalysisDecision("LARGE_SCAN", riskByRows(event, maxExplainRows),
                    "慢 SQL 扫描行数或预估访问行数较大，疑似大范围扫描导致耗时升高。",
                    "收敛查询条件、减少返回列和扫描范围；对分页、排序、时间范围查询补充合适索引。",
                    buildIndexSuggestion(sqlText, worstExplain == null ? firstTable(tableInfos) : worstExplain.tableName(), indexInfos),
                    true, BigDecimal.valueOf(0.76));
        }
        if (lowerSql.matches("(?s).*\\blimit\\s+\\d{4,}\\s*,\\s*\\d+.*")
                || lowerSql.matches("(?s).*\\boffset\\s+\\d{4,}.*")) {
            return new AnalysisDecision("BAD_PAGINATION", "P2",
                    "SQL 使用深分页，offset 越大扫描和丢弃的数据越多，容易形成慢查询。",
                    "改为基于游标或上次最大 ID/时间的翻页方式，并保证排序字段有稳定索引。",
                    null, false, BigDecimal.valueOf(0.74));
        }
        if (event.getDurationMs() != null && event.getDurationMs() >= 60000) {
            return new AnalysisDecision("LONG_TRANSACTION", "P1",
                    "SQL 执行时间超过 60 秒，但当前未命中明确索引或锁等待规则，需按长事务/长查询继续排查。",
                    "结合 processlist、事务快照、业务调用链和表数据规模定位；必要时拆分查询或异步化。",
                    null, true, BigDecimal.valueOf(0.62));
        }
        return new AnalysisDecision("UNKNOWN", "P2",
                "当前只读上下文未发现明确根因，需要结合业务调用链、参数分布和历史执行计划进一步确认。",
                "保留 SQL、EXPLAIN、表规模和索引信息，交由开发与 DBA 共同复核。",
                null, false, BigDecimal.valueOf(0.50));
    }

    private boolean isLargeScan(SlowSqlEvent event, List<TableInfo> tableInfos, long maxExplainRows) {
        if (event.getRowsExamined() != null && event.getRowsExamined() >= 10000) {
            return true;
        }
        if (maxExplainRows >= 10000) {
            return true;
        }
        return tableInfos.stream().anyMatch(table -> table.tableRows() != null && table.tableRows() >= 1000000);
    }

    private String riskByRows(SlowSqlEvent event, long rows) {
        if (event.getDurationMs() != null && event.getDurationMs() >= 60000) {
            return "P1";
        }
        if (event.getRowsExamined() != null && event.getRowsExamined() >= 1000000) {
            return "P1";
        }
        return rows >= 1000000 ? "P1" : "P2";
    }

    private Set<String> extractTableNames(String sqlText) {
        Set<String> tables = new LinkedHashSet<>();
        Matcher matcher = TABLE_PATTERN.matcher(sqlText);
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    private String buildIndexSuggestion(String sqlText, String tableName, List<IndexInfo> indexInfos) {
        if (!StringUtils.hasText(tableName) || hasSecondaryIndex(indexInfos, tableName)) {
            return null;
        }
        Matcher whereMatcher = WHERE_COLUMN_PATTERN.matcher(sqlText);
        if (!whereMatcher.find()) {
            return null;
        }
        Set<String> columns = new LinkedHashSet<>();
        Matcher columnMatcher = CONDITION_COLUMN_PATTERN.matcher(whereMatcher.group(1));
        while (columnMatcher.find() && columns.size() < 3) {
            columns.add(columnMatcher.group(1));
        }
        if (columns.isEmpty()) {
            return null;
        }
        String indexName = "idx_argus_" + String.join("_", columns);
        return "ALTER TABLE `" + tableName + "` ADD INDEX `" + indexName + "` (`"
                + String.join("`, `", columns) + "`);";
    }

    private boolean hasSecondaryIndex(List<IndexInfo> indexInfos, String tableName) {
        return indexInfos.stream().anyMatch(index -> tableName.equalsIgnoreCase(index.tableName())
                && !"PRIMARY".equalsIgnoreCase(index.indexName()));
    }

    private boolean containsLockState(SlowSqlEvent event) {
        String state = event.getProcessState();
        return StringUtils.hasText(state) && state.toUpperCase(Locale.ROOT).contains("LOCK");
    }

    private String firstTable(List<TableInfo> tableInfos) {
        return tableInfos.isEmpty() ? null : tableInfos.get(0).tableName();
    }

    private void markFailed(SlowSqlEvent event, String message) {
        event.setAnalysisStatus("FAILED");
        event.setRootCause(message);
        slowSqlEventMapper.updateById(event);
    }

    private SlowSqlAnalysisResult result(SlowSqlEvent event, String message) {
        return new SlowSqlAnalysisResult(event.getId(), event.getAnalysisStatus(), event.getCauseType(),
                event.getRiskLevel(), message);
    }

    private record AnalysisDecision(
            String causeType,
            String riskLevel,
            String rootCause,
            String optimizationSuggestion,
            String indexSuggestionSql,
            Boolean needDba,
            BigDecimal confidence
    ) {
    }
}
