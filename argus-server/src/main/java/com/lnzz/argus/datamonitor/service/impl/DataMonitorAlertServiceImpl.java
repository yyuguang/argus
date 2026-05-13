package com.lnzz.argus.datamonitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.datamonitor.entity.ConnectionPoolSnapshot;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DbLockEvent;
import com.lnzz.argus.datamonitor.entity.LogQualityIssue;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.ConnectionPoolSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DbLockEventMapper;
import com.lnzz.argus.datamonitor.mapper.LogQualityIssueMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.DataMonitorAlertService;
import com.lnzz.argus.notification.entity.NotificationRecord;
import com.lnzz.argus.common.enums.NotificationStatus;
import com.lnzz.argus.notification.mapper.NotificationRecordMapper;
import com.lnzz.argus.notification.service.WechatWebhookClient;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.mapper.ScmConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据监控告警服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class DataMonitorAlertServiceImpl implements DataMonitorAlertService {

    private final SlowSqlEventMapper slowSqlEventMapper;
    private final DbLockEventMapper dbLockEventMapper;
    private final ConnectionPoolSnapshotMapper poolSnapshotMapper;
    private final LogQualityIssueMapper logQualityIssueMapper;
    private final DataMonitorConfigMapper dataMonitorConfigMapper;
    private final ScmConfigMapper scmConfigMapper;
    private final NotificationRecordMapper notificationRecordMapper;
    private final WechatWebhookClient wechatWebhookClient;

    @Override
    public List<DataMonitorAlertResult> alertPending() {
        List<DataMonitorAlertResult> results = new ArrayList<>();
        slowSqlEventMapper.selectList(new LambdaQueryWrapper<SlowSqlEvent>()
                        .in(SlowSqlEvent::getRiskLevel, List.of("P1", "P2"))
                        .notIn(SlowSqlEvent::getAnalysisStatus, List.of("IGNORED", "CONFIRMED"))
                        .orderByDesc(SlowSqlEvent::getOccurredAt)
                        .last("limit 20"))
                .forEach(event -> results.add(alertSlowSql(event)));
        dbLockEventMapper.selectList(new LambdaQueryWrapper<DbLockEvent>()
                        .in(DbLockEvent::getRiskLevel, List.of("P1", "P2"))
                        .eq(DbLockEvent::getStatus, "NEW")
                        .orderByDesc(DbLockEvent::getOccurredAt)
                        .last("limit 20"))
                .forEach(event -> results.add(alertLockEvent(event)));
        poolSnapshotMapper.selectList(new LambdaQueryWrapper<ConnectionPoolSnapshot>()
                        .isNotNull(ConnectionPoolSnapshot::getRiskType)
                        .orderByDesc(ConnectionPoolSnapshot::getCollectedAt)
                        .last("limit 20"))
                .forEach(snapshot -> results.add(alertPoolRisk(snapshot)));
        logQualityIssueMapper.selectList(new LambdaQueryWrapper<LogQualityIssue>()
                        .eq(LogQualityIssue::getStatus, "NEW")
                        .in(LogQualityIssue::getSeverity, List.of("P1", "P2"))
                        .orderByDesc(LogQualityIssue::getOccurredAt)
                        .last("limit 20"))
                .forEach(issue -> results.add(alertLogQualityIssue(issue)));
        return results;
    }

    private DataMonitorAlertResult alertSlowSql(SlowSqlEvent event) {
        String dedupKey = "SLOW_SQL:" + event.getDatasourceId() + ":" + event.getSqlFingerprint();
        if (isRecentlySent("SLOW_SQL_EVENT", event.getId(), dedupKey, 30)) {
            return new DataMonitorAlertResult("SLOW_SQL_EVENT", event.getId(), false, "短窗口内已通知");
        }
        String content = "### Argus 慢 SQL 告警\n"
                + "- 应用：" + event.getAppName() + "\n"
                + "- 等级：" + event.getRiskLevel() + "\n"
                + "- 根因：" + nvl(event.getCauseType()) + "\n"
                + "- 耗时：" + nvl(event.getDurationMs()) + "ms\n"
                + "- SQL：" + truncate(event.getSqlTextMasked(), 300) + "\n"
                + "- 索引建议（仅展示，不执行）：\n`" + nvl(event.getIndexSuggestionSql()) + "`";
        return send(event.getMonitorConfigId(), "SLOW_SQL_EVENT", event.getId(), dedupKey, content);
    }

    private DataMonitorAlertResult alertLockEvent(DbLockEvent event) {
        String dedupKey = "LOCK:" + event.getDatasourceId() + ":" + event.getEventFingerprint();
        if (isRecentlySent("DB_LOCK_EVENT", event.getId(), dedupKey, 30)) {
            return new DataMonitorAlertResult("DB_LOCK_EVENT", event.getId(), false, "短窗口内已通知");
        }
        String content = "### Argus 锁等待告警\n"
                + "- 应用：" + event.getAppName() + "\n"
                + "- 等级：" + event.getRiskLevel() + "\n"
                + "- 等待时长：" + nvl(event.getWaitSeconds()) + "s\n"
                + "- 锁表：" + nvl(event.getLockTable()) + "\n"
                + "- 等待 SQL：" + truncate(event.getWaitingSql(), 300);
        DataMonitorConfig config = findMonitorConfig(event.getAppName(), event.getEnvironment());
        return send(config == null ? null : config.getId(), "DB_LOCK_EVENT", event.getId(), dedupKey, content);
    }

    private DataMonitorAlertResult alertPoolRisk(ConnectionPoolSnapshot snapshot) {
        String dedupKey = "POOL:" + snapshot.getMonitorConfigId() + ":" + snapshot.getDatasourceName()
                + ":" + snapshot.getRiskType();
        if (isRecentlySent("POOL_SNAPSHOT", snapshot.getId(), dedupKey, 30)) {
            return new DataMonitorAlertResult("POOL_SNAPSHOT", snapshot.getId(), false, "短窗口内已通知");
        }
        String content = "### Argus 连接池告警\n"
                + "- 应用：" + snapshot.getAppName() + "\n"
                + "- 数据源：" + snapshot.getDatasourceName() + "\n"
                + "- 风险：" + snapshot.getRiskType() + " / " + snapshot.getRiskLevel() + "\n"
                + "- 活跃/最大：" + snapshot.getActiveConnections() + "/" + snapshot.getMaxConnections() + "\n"
                + "- 等待线程：" + nvl(snapshot.getWaitingThreads());
        return send(snapshot.getMonitorConfigId(), "POOL_SNAPSHOT", snapshot.getId(), dedupKey, content);
    }

    private DataMonitorAlertResult alertLogQualityIssue(LogQualityIssue issue) {
        String dedupKey = "LOG_QUALITY:" + issue.getLogTableConfigId() + ":" + issue.getIssueType();
        if (isRecentlySent("LOG_QUALITY_ISSUE", issue.getId(), dedupKey, 30)) {
            return new DataMonitorAlertResult("LOG_QUALITY_ISSUE", issue.getId(), false, "短窗口内已通知");
        }
        String content = "### Argus 日志质量告警\n"
                + "- 应用：" + issue.getAppName() + "\n"
                + "- 日志表：" + issue.getTableName() + "\n"
                + "- 问题：" + issue.getIssueType() + " / " + issue.getSeverity() + "\n"
                + "- 数量：" + issue.getIssueCount() + "\n"
                + "- 建议：" + nvl(issue.getSuggestion());
        DataMonitorConfig config = findMonitorConfig(issue.getAppName(), issue.getEnvironment());
        return send(config == null ? null : config.getId(), "LOG_QUALITY_ISSUE", issue.getId(), dedupKey, content);
    }

    private DataMonitorAlertResult send(Long monitorConfigId,
                                        String refType,
                                        Long refId,
                                        String dedupKey,
                                        String content) {
        DataMonitorConfig monitorConfig = monitorConfigId == null ? null : dataMonitorConfigMapper.selectById(monitorConfigId);
        if (monitorConfig == null) {
            return new DataMonitorAlertResult(refType, refId, false, "未找到监控配置");
        }
        ScmConfig scmConfig = scmConfigMapper.selectById(monitorConfig.getScmConfigId());
        if (scmConfig == null || !scmConfig.isWechatNotificationEnabled()
                || !StringUtils.hasText(scmConfig.getWechatNotifyWebhook())) {
            return new DataMonitorAlertResult(refType, refId, false, "SCM 企业微信通知未配置或未启用");
        }
        boolean sent = wechatWebhookClient.sendMarkdown("data-monitor", content, scmConfig.getWechatNotifyWebhook());
        saveRecord(refType, refId, dedupKey, content, sent);
        return new DataMonitorAlertResult(refType, refId, sent, sent ? "发送成功" : "发送失败");
    }

    private boolean isRecentlySent(String refType, Long refId, String dedupKey, int minutes) {
        Long count = notificationRecordMapper.selectCount(new LambdaQueryWrapper<NotificationRecord>()
                .eq(NotificationRecord::getRefType, refType)
                .eq(NotificationRecord::getStatus, NotificationStatus.SENT.getCode())
                .ge(NotificationRecord::getCreateTime, LocalDateTime.now().minusMinutes(minutes))
                .and(wrapper -> wrapper.eq(NotificationRecord::getRefId, refId)
                        .or()
                        .like(NotificationRecord::getContentSummary, dedupKey)));
        return count != null && count > 0;
    }

    private void saveRecord(String refType, Long refId, String dedupKey, String content, boolean sent) {
        NotificationRecord record = new NotificationRecord();
        record.setType("DATA_MONITOR_ALERT");
        record.setChannel("WECHAT");
        record.setRefType(refType);
        record.setRefId(refId);
        record.setContentSummary(truncate(dedupKey + "\n" + content, 500));
        record.setStatus(sent ? NotificationStatus.SENT.getCode() : NotificationStatus.FAILED.getCode());
        record.setRetryCount(0);
        record.setSentAt(sent ? LocalDateTime.now() : null);
        notificationRecordMapper.insert(record);
    }

    private DataMonitorConfig findMonitorConfig(String appName, String environment) {
        return dataMonitorConfigMapper.selectOne(new LambdaQueryWrapper<DataMonitorConfig>()
                .eq(DataMonitorConfig::getAppName, appName)
                .eq(DataMonitorConfig::getEnvironment, environment)
                .last("limit 1"));
    }

    private String truncate(Object value, int maxLength) {
        String text = nvl(value);
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String nvl(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
