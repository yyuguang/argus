package com.lnzz.argus.datamonitor.service;

import com.lnzz.argus.datamonitor.entity.LogQualityIssue;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;

/**
 * 数据监控人工处理服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface DataMonitorActionService {

    SlowSqlEvent ignoreSlowSql(Long eventId, ActionRequest request);

    SlowSqlEvent confirmSlowSql(Long eventId, SlowSqlConfirmRequest request);

    LogQualityIssue ignoreLogQualityIssue(Long issueId, ActionRequest request);

    record ActionRequest(String operator, String reason) {
    }

    record SlowSqlConfirmRequest(
            String operator,
            String confirmedCauseType,
            String confirmedConclusion,
            Boolean acceptedIndexSuggestion
    ) {
    }
}
