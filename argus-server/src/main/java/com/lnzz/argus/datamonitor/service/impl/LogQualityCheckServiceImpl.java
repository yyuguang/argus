package com.lnzz.argus.datamonitor.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.InterfaceLogTableConfig;
import com.lnzz.argus.datamonitor.entity.LogQualityCheckResult;
import com.lnzz.argus.datamonitor.entity.LogQualityIssue;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.InterfaceLogTableConfigMapper;
import com.lnzz.argus.datamonitor.mapper.LogQualityCheckResultMapper;
import com.lnzz.argus.datamonitor.mapper.LogQualityIssueMapper;
import com.lnzz.argus.datamonitor.service.DataSourceSecretCodec;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector.LogQualityRules;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector.LogTableScanMetrics;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector.ScanWindow;
import com.lnzz.argus.datamonitor.service.LogQualityCheckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 接口日志表质量巡检服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogQualityCheckServiceImpl implements LogQualityCheckService {

    private final InterfaceLogTableConfigMapper logTableConfigMapper;
    private final DataSourceConfigMapper dataSourceConfigMapper;
    private final LogQualityCheckResultMapper checkResultMapper;
    private final LogQualityIssueMapper issueMapper;
    private final DataSourceSecretCodec secretCodec;
    private final InterfaceLogTableInspector inspector;

    @Override
    public List<LogQualityCheckResponse> checkAllEnabled() {
        List<InterfaceLogTableConfig> configs = logTableConfigMapper.selectList(
                new LambdaQueryWrapper<InterfaceLogTableConfig>()
                        .eq(InterfaceLogTableConfig::getEnabled, true)
                        .orderByAsc(InterfaceLogTableConfig::getMonitorConfigId)
                        .orderByAsc(InterfaceLogTableConfig::getTableName));
        List<LogQualityCheckResponse> responses = new ArrayList<>();
        for (InterfaceLogTableConfig config : configs) {
            try {
                responses.add(check(config));
            } catch (Exception e) {
                log.warn("接口日志表质量巡检失败，已降级继续: configId={}, table={}, message={}",
                        config.getId(), config.getTableName(), e.getMessage());
                responses.add(failure(config, e.getMessage()));
            }
        }
        return responses;
    }

    @Override
    public LogQualityCheckResponse checkConfig(Long configId) {
        InterfaceLogTableConfig config = logTableConfigMapper.selectById(configId);
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND, "接口日志表配置不存在: " + configId);
        }
        return check(config);
    }

    private LogQualityCheckResponse check(InterfaceLogTableConfig config) {
        DataSourceConfig datasource = dataSourceConfigMapper.selectById(config.getDatasourceId());
        if (datasource == null || !Boolean.TRUE.equals(datasource.getReadonly())) {
            throw new BizException(ResultCode.NOT_FOUND, "只读数据源配置不存在: " + config.getDatasourceId());
        }
        LocalDateTime now = LocalDateTime.now();
        LogQualityRules rules = parseRules(config.getQualityRules());
        ScanWindow window = buildWindow(config, rules, now);
        LogTableScanMetrics metrics = inspector.scan(datasource, secretCodec.decrypt(datasource.getPasswordSecret()),
                config, rules, window);
        Score score = calculateScore(metrics, rules, now);

        LogQualityCheckResult result = new LogQualityCheckResult();
        result.setLogTableConfigId(config.getId());
        result.setMonitorConfigId(config.getMonitorConfigId());
        result.setDatasourceId(config.getDatasourceId());
        result.setAppName(config.getAppName());
        result.setEnvironment(config.getEnvironment());
        result.setTableName(config.getTableName());
        result.setCheckWindowStart(window.windowStart());
        result.setCheckWindowEnd(window.windowEnd());
        result.setTotalCount(metrics.totalCount());
        result.setIssueCount(score.issueCount());
        result.setQualityScore(score.totalScore());
        result.setQualityLevel(level(score.totalScore()));
        result.setCompletenessScore(score.completenessScore());
        result.setTimelinessScore(score.timelinessScore());
        result.setUniquenessScore(score.uniquenessScore());
        result.setValidityScore(score.validityScore());
        result.setConsistencyScore(score.consistencyScore());
        result.setGrowthRiskScore(score.growthRiskScore());
        result.setStatus("DONE");
        checkResultMapper.insert(result);
        saveIssues(config, result, metrics, rules, now);
        advanceCursor(config, metrics, window);
        return new LogQualityCheckResponse(config.getId(), result.getId(), config.getTableName(), true,
                metrics.totalCount(), score.issueCount(), score.totalScore(), result.getQualityLevel(), "巡检完成");
    }

    private void saveIssues(InterfaceLogTableConfig config,
                            LogQualityCheckResult result,
                            LogTableScanMetrics metrics,
                            LogQualityRules rules,
                            LocalDateTime now) {
        addIssue(config, result, "REQUIRED_FIELD_EMPTY", metrics.nullRequiredCount(), "P2",
                "关键字段存在空值", "补齐字段映射和日志写入逻辑，避免接口编码、时间、响应体等关键字段为空", metrics, now);
        boolean noNewData = rules.noNewDataMinutes() != null
                && (metrics.latestRequestTime() == null
                || metrics.latestRequestTime().isBefore(now.minusMinutes(rules.noNewDataMinutes())));
        addIssue(config, result, "NO_NEW_DATA", noNewData ? 1 : 0, "P1",
                "接口日志表超过阈值时间无新增", "确认业务系统日志写入链路、定时任务和数据库连接状态", metrics, now);
        addIssue(config, result, "DUPLICATE_LOG",
                metrics.duplicateRequestIdCount() + metrics.duplicateTraceIdCount(), "P2",
                "requestId 或 traceId 存在重复日志", "确认接口重试、幂等键和日志唯一键写入逻辑", metrics, now);
        addIssue(config, result, "INVALID_TIME", metrics.invalidTimeCount(), "P2",
                "存在响应时间早于请求时间或负耗时", "修正时间字段写入来源，统一时区和时间类型", metrics, now);
        addIssue(config, result, "INVALID_STATUS", metrics.invalidStatusCount(), "P2",
                "存在非法状态码", "收敛状态码枚举，并对未知状态做兼容映射", metrics, now);
        addIssue(config, result, "STATUS_CONFLICT", metrics.statusConflictCount(), "P2",
                "表状态与响应体状态存在冲突", "确认响应解析和状态字段写入逻辑是否一致", metrics, now);
        addIssue(config, result, "RESPONSE_BODY_QUALITY",
                metrics.emptyResponseCount() + metrics.oversizeResponseCount(), "P3",
                "响应体为空或超过大小阈值", "检查响应体截断、脱敏和大字段归档策略", metrics, now);
        boolean growthRisk = rules.maxTableRows() != null && metrics.tableRows() > rules.maxTableRows();
        addIssue(config, result, "GROWTH_RISK", growthRisk ? 1 : 0, "P2",
                "日志表行数超过增长风险阈值", "评估分区、归档、清理和冷热数据拆分策略", metrics, now);
    }

    private void addIssue(InterfaceLogTableConfig config,
                          LogQualityCheckResult result,
                          String issueType,
                          long issueCount,
                          String severity,
                          String description,
                          String suggestion,
                          LogTableScanMetrics metrics,
                          LocalDateTime now) {
        if (issueCount <= 0) {
            return;
        }
        LogQualityIssue issue = new LogQualityIssue();
        issue.setCheckResultId(result.getId());
        issue.setLogTableConfigId(config.getId());
        issue.setAppName(config.getAppName());
        issue.setEnvironment(config.getEnvironment());
        issue.setTableName(config.getTableName());
        issue.setIssueType(issueType);
        issue.setSeverity(severity);
        issue.setIssueCount(issueCount);
        issue.setSampleRecordId(metrics.sampleRecordId());
        issue.setSamplePayload(metrics.samplePayload());
        issue.setDescription(description);
        issue.setSuggestion(suggestion);
        issue.setStatus("NEW");
        issue.setOccurredAt(now);
        issueMapper.insert(issue);
    }

    private Score calculateScore(LogTableScanMetrics metrics, LogQualityRules rules, LocalDateTime now) {
        int completeness = rateScore(metrics.totalCount(), metrics.nullRequiredCount());
        boolean noNewData = rules.noNewDataMinutes() != null
                && (metrics.latestRequestTime() == null
                || metrics.latestRequestTime().isBefore(now.minusMinutes(rules.noNewDataMinutes())));
        int timeliness = noNewData ? 0 : 100;
        int uniqueness = rateScore(metrics.totalCount(), metrics.duplicateRequestIdCount() + metrics.duplicateTraceIdCount());
        int validity = rateScore(metrics.totalCount(), metrics.invalidTimeCount() + metrics.invalidStatusCount());
        int consistency = rateScore(metrics.totalCount(), metrics.statusConflictCount());
        int growth = rules.maxTableRows() != null && metrics.tableRows() > rules.maxTableRows() ? 40 : 100;
        int total = Math.round(completeness * 0.25F + timeliness * 0.20F + uniqueness * 0.15F
                + validity * 0.15F + consistency * 0.15F + growth * 0.10F);
        long issueCount = metrics.nullRequiredCount()
                + (noNewData ? 1 : 0)
                + metrics.duplicateRequestIdCount()
                + metrics.duplicateTraceIdCount()
                + metrics.invalidTimeCount()
                + metrics.invalidStatusCount()
                + metrics.statusConflictCount()
                + metrics.emptyResponseCount()
                + metrics.oversizeResponseCount()
                + (rules.maxTableRows() != null && metrics.tableRows() > rules.maxTableRows() ? 1 : 0);
        return new Score(total, issueCount, completeness, timeliness, uniqueness, validity, consistency, growth);
    }

    private int rateScore(long total, long issue) {
        if (total <= 0) {
            return 100;
        }
        double rate = issue * 100.0D / total;
        return Math.max(0, 100 - (int) Math.ceil(rate * 10));
    }

    private String level(int score) {
        if (score >= 90) {
            return "A";
        }
        if (score >= 80) {
            return "B";
        }
        if (score >= 70) {
            return "C";
        }
        if (score >= 60) {
            return "D";
        }
        return "F";
    }

    private ScanWindow buildWindow(InterfaceLogTableConfig config, LogQualityRules rules, LocalDateTime now) {
        if ("TIME_WINDOW".equalsIgnoreCase(config.getScanMode())) {
            int minutes = rules.noNewDataMinutes() == null ? 10 : Math.max(1, rules.noNewDataMinutes());
            return new ScanWindow("TIME_WINDOW", config.getLastScanValue(), now.minusMinutes(minutes), now);
        }
        return new ScanWindow("ID_INCREMENT", config.getLastScanValue(), null, now);
    }

    private void advanceCursor(InterfaceLogTableConfig config, LogTableScanMetrics metrics, ScanWindow window) {
        if ("ID_INCREMENT".equalsIgnoreCase(config.getScanMode()) && StringUtils.hasText(metrics.maxPrimaryKeyValue())) {
            config.setLastScanValue(metrics.maxPrimaryKeyValue());
        } else if ("TIME_WINDOW".equalsIgnoreCase(config.getScanMode()) && window.windowEnd() != null) {
            config.setLastScanValue(window.windowEnd().toString());
        }
        logTableConfigMapper.updateById(config);
    }

    private LogQualityRules parseRules(String json) {
        if (!StringUtils.hasText(json)) {
            return defaultRules();
        }
        LogQualityRules rules = JSON.parseObject(json, LogQualityRules.class);
        return rules == null ? defaultRules() : rules;
    }

    private LogQualityRules defaultRules() {
        return new LogQualityRules(Set.of(), 10, 5, 1, 512, null, Set.of("200", "0", "SUCCESS"));
    }

    private LogQualityCheckResponse failure(InterfaceLogTableConfig config, String message) {
        LogQualityCheckResult result = new LogQualityCheckResult();
        result.setLogTableConfigId(config.getId());
        result.setMonitorConfigId(config.getMonitorConfigId());
        result.setDatasourceId(config.getDatasourceId());
        result.setAppName(config.getAppName());
        result.setEnvironment(config.getEnvironment());
        result.setTableName(config.getTableName());
        result.setTotalCount(0L);
        result.setIssueCount(0L);
        result.setStatus("FAILED");
        result.setErrorMessage(message);
        checkResultMapper.insert(result);
        return new LogQualityCheckResponse(config.getId(), result.getId(), config.getTableName(), false,
                0, 0, null, null, message);
    }

    private record Score(
            int totalScore,
            long issueCount,
            int completenessScore,
            int timelinessScore,
            int uniquenessScore,
            int validityScore,
            int consistencyScore,
            int growthRiskScore
    ) {
    }
}
