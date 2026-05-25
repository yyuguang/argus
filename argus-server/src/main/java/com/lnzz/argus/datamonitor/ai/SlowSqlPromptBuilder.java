package com.lnzz.argus.datamonitor.ai;

import com.lnzz.argus.datamonitor.entity.ConnectionPoolSnapshot;
import com.lnzz.argus.datamonitor.entity.DbLockEvent;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.service.MysqlSlowSqlInspector.ExplainRow;
import com.lnzz.argus.rule.service.RulePromptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 慢 SQL AI 分析 Prompt 组装器。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class SlowSqlPromptBuilder {

    private static final String TEMPLATE_CODE = "SLOW_SQL_ANALYSIS_MAIN";

    private final RulePromptService rulePromptService;

    public String buildPrompt(SlowSqlEvent event,
                              String sqlText,
                              List<ExplainRow> explainRows,
                              DbLockEvent lockEvent,
                              ConnectionPoolSnapshot poolSnapshot,
                              String ruleCauseType,
                              String ruleRiskLevel,
                              String ruleRootCause,
                              String ruleSuggestion,
                              Long scmConfigId) {
        return rulePromptService.getTemplateContent(TEMPLATE_CODE, scmConfigId)
                .replace("{{slowSqlEventSummary}}", buildEventSummary(event))
                .replace("{{sqlText}}", truncate(sqlText, 8000))
                .replace("{{explainResultSection}}", buildExplainSection(explainRows))
                .replace("{{lockContextSection}}", buildLockContextSection(lockEvent))
                .replace("{{poolContextSection}}", buildPoolContextSection(poolSnapshot))
                .replace("{{analysisHintsSection}}", buildAnalysisHintsSection(
                        ruleCauseType, ruleRiskLevel, ruleRootCause, ruleSuggestion));
    }

    private String buildEventSummary(SlowSqlEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 字段 | 值 |\n|---|---|\n");
        appendRow(sb, "应用", event.getAppName());
        appendRow(sb, "环境", event.getEnvironment());
        appendRow(sb, "来源", event.getSourceType());
        appendRow(sb, "耗时(ms)", toStringValue(event.getDurationMs()));
        appendRow(sb, "锁等待(ms)", toStringValue(event.getLockTimeMs()));
        appendRow(sb, "返回行数", toStringValue(event.getRowsSent()));
        appendRow(sb, "扫描行数", toStringValue(event.getRowsExamined()));
        appendRow(sb, "执行状态", event.getProcessState());
        appendRow(sb, "发生时间", String.valueOf(event.getOccurredAt()));
        return sb.toString();
    }

    private String buildExplainSection(List<ExplainRow> explainRows) {
        if (explainRows == null || explainRows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## EXPLAIN 结果\n\n");
        sb.append("| id | type | table | accessType | key | possibleKeys | rows | extra |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");
        for (ExplainRow row : explainRows) {
            sb.append("| ")
                    .append(toStringValue(row.id())).append(" | ")
                    .append(safe(row.selectType())).append(" | ")
                    .append(safe(row.tableName())).append(" | ")
                    .append(safe(row.accessType())).append(" | ")
                    .append(safe(row.keyName())).append(" | ")
                    .append(safe(row.possibleKeys())).append(" | ")
                    .append(toStringValue(row.rows())).append(" | ")
                    .append(safe(row.extra())).append(" |\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildLockContextSection(DbLockEvent lockEvent) {
        if (lockEvent == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 锁等待现场\n\n");
        sb.append("| 字段 | 值 |\n|---|---|\n");
        appendRow(sb, "锁表", lockEvent.getLockTable());
        appendRow(sb, "锁索引", lockEvent.getLockIndex());
        appendRow(sb, "锁类型", lockEvent.getLockType());
        appendRow(sb, "等待时长(秒)", toStringValue(lockEvent.getWaitSeconds()));
        appendRow(sb, "等待线程", toStringValue(lockEvent.getWaitingProcessId()));
        appendRow(sb, "阻塞线程", toStringValue(lockEvent.getBlockingProcessId()));
        appendRow(sb, "风险等级", lockEvent.getRiskLevel());
        if (StringUtils.hasText(lockEvent.getWaitingSql())) {
            sb.append("\n### 等待 SQL\n```sql\n").append(truncate(lockEvent.getWaitingSql(), 3000)).append("\n```\n");
        }
        if (StringUtils.hasText(lockEvent.getBlockingSql())) {
            sb.append("\n### 阻塞 SQL\n```sql\n").append(truncate(lockEvent.getBlockingSql(), 3000)).append("\n```\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildPoolContextSection(ConnectionPoolSnapshot poolSnapshot) {
        if (poolSnapshot == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 连接池现场\n\n");
        sb.append("| 字段 | 值 |\n|---|---|\n");
        appendRow(sb, "连接池类型", poolSnapshot.getPoolType());
        appendRow(sb, "风险类型", poolSnapshot.getRiskType());
        appendRow(sb, "风险等级", poolSnapshot.getRiskLevel());
        appendRow(sb, "活跃连接", toStringValue(poolSnapshot.getActiveConnections()));
        appendRow(sb, "空闲连接", toStringValue(poolSnapshot.getIdleConnections()));
        appendRow(sb, "最大连接", toStringValue(poolSnapshot.getMaxConnections()));
        appendRow(sb, "等待线程", toStringValue(poolSnapshot.getWaitingThreads()));
        appendRow(sb, "平均获取连接耗时(ms)", toStringValue(poolSnapshot.getConnectionAcquireAvgMs()));
        appendRow(sb, "最大获取连接耗时(ms)", toStringValue(poolSnapshot.getConnectionAcquireMaxMs()));
        appendRow(sb, "超时次数", toStringValue(poolSnapshot.getTimeoutCount()));
        appendRow(sb, "错误次数", toStringValue(poolSnapshot.getErrorCount()));
        sb.append("\n");
        return sb.toString();
    }

    private String buildAnalysisHintsSection(String ruleCauseType,
                                             String ruleRiskLevel,
                                             String ruleRootCause,
                                             String ruleSuggestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 规则预判参考\n\n");
        sb.append("- 当前规则根因类型：").append(safe(ruleCauseType)).append("\n");
        sb.append("- 当前规则风险等级：").append(safe(ruleRiskLevel)).append("\n");
        sb.append("- 当前规则根因结论：").append(safe(ruleRootCause)).append("\n");
        sb.append("- 当前规则优化建议：").append(safe(ruleSuggestion)).append("\n\n");
        sb.append("请在此基础上结合 SQL 与上下文重新给出结构化 AI 分析；如果你认为规则预判不准确，可以修正，但必须写清证据。\n");
        return sb.toString();
    }

    private void appendRow(StringBuilder sb, String key, String value) {
        sb.append("| ").append(key).append(" | ").append(safe(value)).append(" |\n");
    }

    private String safe(String value) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        return value.replace("|", "\\|").replace("\n", " ");
    }

    private String toStringValue(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "\n-- 已截断，原始长度: " + text.length() + " --";
    }
}
