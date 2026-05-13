package com.lnzz.argus.knowledge.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错误指纹汇总视图。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class ErrorFingerprintSummary {

    private String errorFingerprint;
    private String appName;
    private String errorType;
    private String severity;
    private String sourceType;
    private String interfaceRef;
    private long eventCount;
    private long occurrenceTotal;
    private long previousOccurrenceTotal;
    private long increaseTotal;
    private LocalDateTime firstOccurredAt;
    private LocalDateTime lastOccurredAt;
}
