package com.lnzz.argus.datamonitor.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.datamonitor.entity.LogQualityIssue;
import com.lnzz.argus.datamonitor.entity.SlowSqlActionLog;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import com.lnzz.argus.datamonitor.mapper.LogQualityIssueMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlActionLogMapper;
import com.lnzz.argus.datamonitor.mapper.SlowSqlEventMapper;
import com.lnzz.argus.datamonitor.service.DataMonitorActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 数据监控人工处理服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class DataMonitorActionServiceImpl implements DataMonitorActionService {

    private final SlowSqlEventMapper slowSqlEventMapper;
    private final LogQualityIssueMapper logQualityIssueMapper;
    private final SlowSqlActionLogMapper actionLogMapper;

    @Override
    public SlowSqlEvent ignoreSlowSql(Long eventId, ActionRequest request) {
        SlowSqlEvent event = requireSlowSql(eventId);
        String before = event.getAnalysisStatus();
        event.setAnalysisStatus("IGNORED");
        event.setRootCause(appendReason(event.getRootCause(), reason(request)));
        slowSqlEventMapper.updateById(event);
        saveAction(event, "IGNORE", operator(request), reason(request), before, "IGNORED", null);
        return event;
    }

    @Override
    public SlowSqlEvent confirmSlowSql(Long eventId, SlowSqlConfirmRequest request) {
        SlowSqlEvent event = requireSlowSql(eventId);
        String before = event.getAnalysisStatus();
        if (request != null && StringUtils.hasText(request.confirmedCauseType())) {
            event.setCauseType(request.confirmedCauseType().trim());
        }
        if (request != null && StringUtils.hasText(request.confirmedConclusion())) {
            event.setRootCause(request.confirmedConclusion().trim());
        }
        event.setAnalysisStatus("CONFIRMED");
        slowSqlEventMapper.updateById(event);
        saveAction(event, "CONFIRM", request == null ? "system" : safeOperator(request.operator()),
                request == null ? null : request.confirmedConclusion(), before, "CONFIRMED",
                JSON.toJSONString(Map.of(
                        "confirmedCauseType", event.getCauseType(),
                        "acceptedIndexSuggestion", request != null && Boolean.TRUE.equals(request.acceptedIndexSuggestion()),
                        "indexSuggestionSql", event.getIndexSuggestionSql() == null ? "" : event.getIndexSuggestionSql()
                )));
        return event;
    }

    @Override
    public LogQualityIssue ignoreLogQualityIssue(Long issueId, ActionRequest request) {
        LogQualityIssue issue = logQualityIssueMapper.selectById(issueId);
        if (issue == null) {
            throw new BizException(ResultCode.NOT_FOUND, "日志质量问题不存在: " + issueId);
        }
        issue.setStatus("IGNORED");
        issue.setSuggestion(appendReason(issue.getSuggestion(), reason(request)));
        logQualityIssueMapper.updateById(issue);
        return issue;
    }

    private SlowSqlEvent requireSlowSql(Long eventId) {
        SlowSqlEvent event = slowSqlEventMapper.selectById(eventId);
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "慢 SQL 事件不存在: " + eventId);
        }
        return event;
    }

    private void saveAction(SlowSqlEvent event,
                            String actionType,
                            String operator,
                            String reason,
                            String beforeStatus,
                            String afterStatus,
                            String detailJson) {
        SlowSqlActionLog log = new SlowSqlActionLog();
        log.setSlowSqlEventId(event.getId());
        log.setActionType(actionType);
        log.setOperator(operator);
        log.setReason(reason);
        log.setBeforeStatus(beforeStatus);
        log.setAfterStatus(afterStatus);
        log.setDetailJson(detailJson);
        actionLogMapper.insert(log);
    }

    private String appendReason(String original, String reason) {
        if (!StringUtils.hasText(reason)) {
            return original;
        }
        return (StringUtils.hasText(original) ? original + "\n" : "") + "人工处理：" + reason;
    }

    private String operator(ActionRequest request) {
        return request == null ? "system" : safeOperator(request.operator());
    }

    private String reason(ActionRequest request) {
        return request == null ? null : request.reason();
    }

    private String safeOperator(String operator) {
        return StringUtils.hasText(operator) ? operator.trim() : "system";
    }
}
