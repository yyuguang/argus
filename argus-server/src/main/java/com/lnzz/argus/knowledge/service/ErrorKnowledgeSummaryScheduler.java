package com.lnzz.argus.knowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 错误知识周期汇总任务。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorKnowledgeSummaryScheduler {

    private final ErrorKnowledgeSummaryService summaryService;

    @Scheduled(cron = "${argus.error.summary.hourly-cron:0 0 * * * *}")
    public void hourlySummary() {
        var highFrequency = summaryService.findHighFrequency(1, 5, 20);
        var newFingerprints = summaryService.findNewFingerprints(1, 20);
        var surgingFingerprints = summaryService.findSurgingFingerprints(1, 5, 20);
        log.info("错误知识小时汇总完成: highFrequency={}, newFingerprints={}, surgingFingerprints={}",
                highFrequency.size(), newFingerprints.size(), surgingFingerprints.size());
    }

    @Scheduled(cron = "${argus.error.summary.daily-cron:0 10 0 * * *}")
    public void dailySummary() {
        var highFrequency = summaryService.findHighFrequency(24, 10, 50);
        log.info("错误知识日报汇总完成: highFrequency={}", highFrequency.size());
    }

    @Scheduled(cron = "${argus.error.summary.weekly-cron:0 30 0 * * MON}")
    public void weeklySummary() {
        var whitelistCandidates = summaryService.findWhitelistCandidates(20);
        log.info("错误知识周汇总完成: whitelistCandidates={}", whitelistCandidates.size());
    }
}
