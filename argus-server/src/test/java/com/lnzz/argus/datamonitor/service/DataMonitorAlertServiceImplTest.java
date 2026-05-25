package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.ConnectionPoolSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DbLockEventMapper;
import com.lnzz.argus.datamonitor.mapper.LogQualityIssueMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.DataMonitorAlertService.DataMonitorAlertResult;
import com.lnzz.argus.datamonitor.service.impl.DataMonitorAlertServiceImpl;
import com.lnzz.argus.notification.mapper.NotificationRecordMapper;
import com.lnzz.argus.notification.service.ScmNotificationDispatcher;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.mapper.ScmConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DataMonitorAlertService - 数据监控告警")
class DataMonitorAlertServiceImplTest {

    @Test
    @DisplayName("慢 SQL 告警复用 SCM 企业微信 webhook 且展示索引建议")
    void alertSlowSqlUsesScmWebhook() {
        Fixture fixture = new Fixture();
        when(fixture.notificationRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(fixture.scmNotificationDispatcher.dispatchMarkdown(any(), isNull(), eq("data-monitor"),
                eq("Argus 数据监控告警"), contains("索引建议"), eq("DATA_MONITOR_ALERT"),
                eq(1L), eq("SLOW_SQL_EVENT"), isNull()))
                .thenReturn(new ScmNotificationDispatcher.DispatchResult(true, 1, 1, "发送成功"));

        List<DataMonitorAlertResult> results = fixture.service.alertPending();

        assertEquals(1, results.size());
        assertTrue(results.get(0).sent());
        verify(fixture.scmNotificationDispatcher).dispatchMarkdown(any(), isNull(), eq("data-monitor"),
                eq("Argus 数据监控告警"), contains("索引建议"), eq("DATA_MONITOR_ALERT"),
                eq(1L), eq("SLOW_SQL_EVENT"), isNull());
    }

    @Test
    @DisplayName("短窗口内已通知则跳过重复告警")
    void alertSkipsRecentlySentSlowSql() {
        Fixture fixture = new Fixture();
        when(fixture.notificationRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        List<DataMonitorAlertResult> results = fixture.service.alertPending();

        assertEquals("短窗口内已通知", results.get(0).message());
        verify(fixture.scmNotificationDispatcher, never()).dispatchMarkdown(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static class Fixture {
        private final SlowSqlEventMapper slowSqlEventMapper = mock(SlowSqlEventMapper.class);
        private final DbLockEventMapper lockEventMapper = mock(DbLockEventMapper.class);
        private final ConnectionPoolSnapshotMapper poolSnapshotMapper = mock(ConnectionPoolSnapshotMapper.class);
        private final LogQualityIssueMapper issueMapper = mock(LogQualityIssueMapper.class);
        private final DataMonitorConfigMapper configMapper = mock(DataMonitorConfigMapper.class);
        private final ScmConfigMapper scmConfigMapper = mock(ScmConfigMapper.class);
        private final NotificationRecordMapper notificationRecordMapper = mock(NotificationRecordMapper.class);
        private final ScmNotificationDispatcher scmNotificationDispatcher = mock(ScmNotificationDispatcher.class);
        private final DataMonitorAlertServiceImpl service = new DataMonitorAlertServiceImpl(slowSqlEventMapper,
                lockEventMapper, poolSnapshotMapper, issueMapper, configMapper, scmConfigMapper,
                notificationRecordMapper, scmNotificationDispatcher);

        private Fixture() {
            SlowSqlEvent event = new SlowSqlEvent();
            event.setId(1L);
            event.setMonitorConfigId(10L);
            event.setDatasourceId(100L);
            event.setAppName("oms-product");
            event.setRiskLevel("P1");
            event.setAnalysisStatus("DONE");
            event.setSqlFingerprint("abc");
            event.setSqlTextMasked("select * from orders where user_id = ?");
            event.setIndexSuggestionSql("ALTER TABLE orders ADD INDEX idx_user_id(user_id);");
            event.setOccurredAt(LocalDateTime.now());
            DataMonitorConfig config = new DataMonitorConfig();
            config.setId(10L);
            config.setScmConfigId(99L);
            config.setAppName("oms-product");
            config.setEnvironment("PROD");
            ScmConfig scmConfig = new ScmConfig();
            scmConfig.setId(99L);
            scmConfig.setWechatNotifyEnabled(1);
            scmConfig.setWechatNotifyWebhook("https://wechat");
            when(slowSqlEventMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(event));
            when(lockEventMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(poolSnapshotMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(issueMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(configMapper.selectById(10L)).thenReturn(config);
            when(scmConfigMapper.selectById(99L)).thenReturn(scmConfig);
        }
    }
}
