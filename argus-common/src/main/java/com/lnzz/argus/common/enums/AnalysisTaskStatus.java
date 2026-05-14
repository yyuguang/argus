package com.lnzz.argus.common.enums;

/**
 * 错误分析任务状态。
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum AnalysisTaskStatus {

    PENDING("PENDING", "待执行"),
    RUNNING("RUNNING", "执行中"),
    DONE("DONE", "已完成"),
    FAILED("FAILED", "失败"),
    TIMEOUT("TIMEOUT", "超时"),
    SKIPPED("SKIPPED", "已跳过");

    private final String code;
    private final String name;

    AnalysisTaskStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
