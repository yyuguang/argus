package com.lnzz.argus.datamonitor.service;

import com.lnzz.argus.datamonitor.entity.LogQualityIssue;
import com.lnzz.argus.datamonitor.entity.SlowSqlActionLog;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.LogQualityIssueMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlActionLogMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.DataMonitorActionService.ActionRequest;
import com.lnzz.argus.datamonitor.service.DataMonitorActionService.SlowSqlConfirmRequest;
import com.lnzz.argus.datamonitor.service.impl.DataMonitorActionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DataMonitorActionService - 数据监控人工处理")
class DataMonitorActionServiceImplTest {

    @Test
    @DisplayName("忽略慢 SQL 会更新状态并写入人工处理日志")
    void ignoreSlowSqlWritesActionLog() {
        Fixture fixture = new Fixture();
        SlowSqlEvent event = slowSql();
        when(fixture.slowSqlEventMapper.selectById(1L)).thenReturn(event);

        SlowSqlEvent result = fixture.service.ignoreSlowSql(1L, new ActionRequest("dba", "测试流量"));

        assertEquals("IGNORED", result.getAnalysisStatus());
        ArgumentCaptor<SlowSqlActionLog> logCaptor = ArgumentCaptor.forClass(SlowSqlActionLog.class);
        verify(fixture.actionLogMapper).insert(logCaptor.capture());
        assertEquals("IGNORE", logCaptor.getValue().getActionType());
        assertEquals("PENDING", logCaptor.getValue().getBeforeStatus());
    }

    @Test
    @DisplayName("确认慢 SQL 根因会保留索引建议为展示信息")
    void confirmSlowSqlKeepsIndexSuggestionAsText() {
        Fixture fixture = new Fixture();
        SlowSqlEvent event = slowSql();
        event.setIndexSuggestionSql("ALTER TABLE orders ADD INDEX idx_user_id(user_id);");
        when(fixture.slowSqlEventMapper.selectById(1L)).thenReturn(event);

        SlowSqlEvent result = fixture.service.confirmSlowSql(1L,
                new SlowSqlConfirmRequest("dev", "MISSING_INDEX", "缺少 user_id 索引", true));

        assertEquals("CONFIRMED", result.getAnalysisStatus());
        assertEquals("MISSING_INDEX", result.getCauseType());
        verify(fixture.actionLogMapper).insert(any(SlowSqlActionLog.class));
    }

    @Test
    @DisplayName("忽略日志质量问题会更新状态")
    void ignoreLogQualityIssueUpdatesStatus() {
        Fixture fixture = new Fixture();
        LogQualityIssue issue = new LogQualityIssue();
        issue.setId(9L);
        issue.setStatus("NEW");
        when(fixture.issueMapper.selectById(9L)).thenReturn(issue);

        LogQualityIssue result = fixture.service.ignoreLogQualityIssue(9L, new ActionRequest("dev", "误报"));

        assertEquals("IGNORED", result.getStatus());
        verify(fixture.issueMapper).updateById(issue);
    }

    private SlowSqlEvent slowSql() {
        SlowSqlEvent event = new SlowSqlEvent();
        event.setId(1L);
        event.setAnalysisStatus("PENDING");
        event.setRootCause("待分析");
        return event;
    }

    private static class Fixture {
        private final SlowSqlEventMapper slowSqlEventMapper = mock(SlowSqlEventMapper.class);
        private final LogQualityIssueMapper issueMapper = mock(LogQualityIssueMapper.class);
        private final SlowSqlActionLogMapper actionLogMapper = mock(SlowSqlActionLogMapper.class);
        private final DataMonitorActionServiceImpl service = new DataMonitorActionServiceImpl(slowSqlEventMapper,
                issueMapper, actionLogMapper);
    }
}
