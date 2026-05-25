package com.lnzz.argus.datamonitor.ai;

import com.lnzz.argus.datamonitor.entity.ConnectionPoolSnapshot;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.rule.service.RulePromptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 连接池风险分析 Prompt 组装器。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class ConnectionPoolRiskPromptBuilder {

    private static final String TEMPLATE_CODE = "DB_POOL_RISK_ANALYSIS_MAIN";

    private final RulePromptService rulePromptService;

    public String buildPrompt(ConnectionPoolSnapshot snapshot,
                              SlowSqlEvent relatedSlowSqlEvent,
                              String ruleRiskType,
                              String ruleRiskLevel,
                              Long scmConfigId) {
        return rulePromptService.getTemplateContent(TEMPLATE_CODE, scmConfigId)
                .replace("{{poolRiskSummary}}", buildPoolRiskSummary(snapshot, ruleRiskType, ruleRiskLevel))
                .replace("{{poolMetricSnapshot}}", buildMetricSnapshot(snapshot))
                .replace("{{relatedSlowSqlSection}}", buildRelatedSlowSqlSection(relatedSlowSqlEvent))
                .replace("{{relatedAlertSection}}", buildRelatedAlertSection(snapshot, ruleRiskType, ruleRiskLevel));
    }

    private String buildPoolRiskSummary(ConnectionPoolSnapshot snapshot, String ruleRiskType, String ruleRiskLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 字段 | 值 |\n|---|---|\n");
        appendRow(sb, "应用", snapshot.getAppName());
        appendRow(sb, "环境", snapshot.getEnvironment());
        appendRow(sb, "数据源", snapshot.getDatasourceName());
        appendRow(sb, "连接池类型", snapshot.getPoolType());
        appendRow(sb, "规则风险类型", ruleRiskType);
        appendRow(sb, "规则风险等级", ruleRiskLevel);
        appendRow(sb, "采集时间", String.valueOf(snapshot.getCollectedAt()));
        return sb.toString();
    }

    private String buildMetricSnapshot(ConnectionPoolSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("| 指标 | 值 |\n|---|---|\n");
        appendRow(sb, "活跃连接", toStringValue(snapshot.getActiveConnections()));
        appendRow(sb, "空闲连接", toStringValue(snapshot.getIdleConnections()));
        appendRow(sb, "最大连接", toStringValue(snapshot.getMaxConnections()));
        appendRow(sb, "等待线程", toStringValue(snapshot.getWaitingThreads()));
        appendRow(sb, "平均获取连接耗时(ms)", toStringValue(snapshot.getConnectionAcquireAvgMs()));
        appendRow(sb, "最大获取连接耗时(ms)", toStringValue(snapshot.getConnectionAcquireMaxMs()));
        appendRow(sb, "超时次数", toStringValue(snapshot.getTimeoutCount()));
        appendRow(sb, "错误次数", toStringValue(snapshot.getErrorCount()));
        return sb.toString();
    }

    private String buildRelatedSlowSqlSection(SlowSqlEvent relatedSlowSqlEvent) {
        if (relatedSlowSqlEvent == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## 关联慢 SQL\n\n");
        sb.append("| 字段 | 值 |\n|---|---|\n");
        appendRow(sb, "事件ID", toStringValue(relatedSlowSqlEvent.getId()));
        appendRow(sb, "耗时(ms)", toStringValue(relatedSlowSqlEvent.getDurationMs()));
        appendRow(sb, "来源类型", relatedSlowSqlEvent.getSourceType());
        appendRow(sb, "执行状态", relatedSlowSqlEvent.getProcessState());
        appendRow(sb, "规则根因", relatedSlowSqlEvent.getCauseType());
        if (StringUtils.hasText(relatedSlowSqlEvent.getSqlTextMasked())) {
            sb.append("\n### SQL 摘要\n```sql\n")
                    .append(truncate(relatedSlowSqlEvent.getSqlTextMasked(), 3000))
                    .append("\n```\n");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String buildRelatedAlertSection(ConnectionPoolSnapshot snapshot, String ruleRiskType, String ruleRiskLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 规则预判参考\n\n");
        sb.append("- 当前规则风险类型：").append(safe(ruleRiskType)).append("\n");
        sb.append("- 当前规则风险等级：").append(safe(ruleRiskLevel)).append("\n");
        if (snapshot.getMaxConnections() != null && snapshot.getMaxConnections() > 0
                && snapshot.getActiveConnections() != null) {
            double usage = snapshot.getActiveConnections() * 100D / snapshot.getMaxConnections();
            sb.append("- 当前连接使用率：").append(String.format("%.1f%%", usage)).append("\n");
        }
        sb.append("- 请说明问题更偏向容量不足、慢 SQL 放大、连接泄漏还是数据库响应抖动，并给出业务可执行建议。\n");
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
