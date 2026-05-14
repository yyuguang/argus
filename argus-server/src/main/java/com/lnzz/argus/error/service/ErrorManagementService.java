package com.lnzz.argus.error.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.error.entity.ErrorAnalysisTask;
import com.lnzz.argus.error.entity.ErrorEvent;

import java.util.List;
import java.util.Map;

/**
 * 错误诊断管理台服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ErrorManagementService {

    Page<ErrorEvent> queryEvents(long pageNo, long pageSize, String appName, String environment,
                                 String severity, String status, String keyword);

    Map<String, Object> getDetail(Long eventId);

    List<ErrorAnalysisTask> listAnalysisTasks(Long eventId);

    List<ErrorEvent> listByFingerprint(String fingerprint);

    Map<String, Object> getStats();

    Map<String, Object> analyze(Long eventId, boolean resetAnalyzed);

    Map<String, Object> retryNotify(Long eventId);

    ErrorEvent ignore(Long eventId, String operator, String reason);

    ErrorEvent markFalsePositive(Long eventId, String operator, String reason);

    ErrorEvent adjustSeverity(Long eventId, String severity, String reason);

    Map<String, Object> manualConclusion(Long eventId, String rootCause, String severity,
                                         String fixDescription, String preventionAdvice);
}
