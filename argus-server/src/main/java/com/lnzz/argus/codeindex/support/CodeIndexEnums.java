package com.lnzz.argus.codeindex.support;

import lombok.Getter;

/**
 * @classname: CodeIndexEnums
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: 源码索引领域枚举，供服务层做状态流转、扫描类型和定位结果表达。
 */
public final class CodeIndexEnums {

    private CodeIndexEnums() {
    }

    /**
     * 扫描状态。
     */
    @Getter
    public enum ScanStatus {

        /**
         * 等待扫描。
         */
        PENDING(CodeIndexConstants.ScanStatus.PENDING, "等待扫描"),

        /**
         * 扫描中。
         */
        RUNNING(CodeIndexConstants.ScanStatus.RUNNING, "扫描中"),

        /**
         * 部分成功。
         */
        PARTIAL(CodeIndexConstants.ScanStatus.PARTIAL, "部分成功"),

        /**
         * 扫描成功。
         */
        SUCCESS(CodeIndexConstants.ScanStatus.SUCCESS, "扫描成功"),

        /**
         * 扫描失败。
         */
        FAILED(CodeIndexConstants.ScanStatus.FAILED, "扫描失败");

        private final String code;
        private final String description;

        ScanStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        /**
         * 判断是否为终态。
         *
         * @return true 表示扫描已结束
         */
        public boolean terminal() {
            return this == PARTIAL || this == SUCCESS || this == FAILED;
        }
    }

    /**
     * 扫描任务状态。
     */
    @Getter
    public enum ScanTaskStatus {

        /**
         * 已创建，等待执行。
         */
        PENDING(CodeIndexConstants.ScanTaskStatus.PENDING, "等待执行"),

        /**
         * 正在执行。
         */
        RUNNING(CodeIndexConstants.ScanTaskStatus.RUNNING, "执行中"),

        /**
         * 扫描成功。
         */
        SUCCESS(CodeIndexConstants.ScanTaskStatus.SUCCESS, "扫描成功"),

        /**
         * 扫描失败。
         */
        FAILED(CodeIndexConstants.ScanTaskStatus.FAILED, "扫描失败"),

        /**
         * 已取消。
         */
        CANCELED(CodeIndexConstants.ScanTaskStatus.CANCELED, "已取消"),

        /**
         * 复用已有成功索引。
         */
        REUSED(CodeIndexConstants.ScanTaskStatus.REUSED, "已复用");

        private final String code;
        private final String description;

        ScanTaskStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }

        /**
         * 判断是否为终态。
         *
         * @return true 表示任务已结束
         */
        public boolean terminal() {
            return this == SUCCESS || this == FAILED || this == CANCELED || this == REUSED;
        }
    }

    /**
     * 扫描阶段。
     */
    @Getter
    public enum ScanStage {

        /**
         * 等待执行。
         */
        WAITING(CodeIndexConstants.ScanStage.WAITING, "等待执行"),

        /**
         * 读取 SCM 文件或 archive。
         */
        SCM_READING(CodeIndexConstants.ScanStage.SCM_READING, "读取 SCM 文件"),

        /**
         * 扫描 Maven 模块。
         */
        MODULE_SCANNING(CodeIndexConstants.ScanStage.MODULE_SCANNING, "扫描 Maven 模块"),

        /**
         * 发现源码根。
         */
        SOURCE_ROOT_DISCOVERING(CodeIndexConstants.ScanStage.SOURCE_ROOT_DISCOVERING, "发现源码根"),

        /**
         * 解析 Java 文件。
         */
        JAVA_PARSING(CodeIndexConstants.ScanStage.JAVA_PARSING, "解析 Java 文件"),

        /**
         * 聚合索引。
         */
        INDEX_AGGREGATING(CodeIndexConstants.ScanStage.INDEX_AGGREGATING, "聚合源码索引"),

        /**
         * 持久化索引。
         */
        INDEX_PERSISTING(CodeIndexConstants.ScanStage.INDEX_PERSISTING, "持久化源码索引"),

        /**
         * 完成。
         */
        COMPLETED(CodeIndexConstants.ScanStage.COMPLETED, "完成"),

        /**
         * 失败。
         */
        FAILED(CodeIndexConstants.ScanStage.FAILED, "失败");

        private final String code;
        private final String description;

        ScanStage(String code, String description) {
            this.code = code;
            this.description = description;
        }
    }

    /**
     * 扫描类型。
     */
    @Getter
    public enum ScanType {

        /**
         * 全量扫描。
         */
        FULL(CodeIndexConstants.ScanType.FULL, "全量扫描"),

        /**
         * 增量扫描。
         */
        INCREMENTAL(CodeIndexConstants.ScanType.INCREMENTAL, "增量扫描"),

        /**
         * 模块级重扫。
         */
        MODULE_RESCAN(CodeIndexConstants.ScanType.MODULE_RESCAN, "模块级重扫"),

        /**
         * 强制重建索引。
         */
        REBUILD(CodeIndexConstants.ScanType.REBUILD, "强制重建索引");

        private final String code;
        private final String description;

        ScanType(String code, String description) {
            this.code = code;
            this.description = description;
        }
    }

    /**
     * 触发类型。
     */
    @Getter
    public enum TriggerType {

        /**
         * 首次初始化触发。
         */
        FIRST_INIT(CodeIndexConstants.TriggerType.FIRST_INIT, "首次初始化"),

        /**
         * Webhook 触发。
         */
        WEBHOOK(CodeIndexConstants.TriggerType.WEBHOOK, "Webhook"),

        /**
         * 手动触发。
         */
        MANUAL(CodeIndexConstants.TriggerType.MANUAL, "手动触发"),

        /**
         * 发布回调触发。
         */
        DEPLOY_CALLBACK(CodeIndexConstants.TriggerType.DEPLOY_CALLBACK, "发布回调"),

        /**
         * 定时任务触发。
         */
        SCHEDULED(CodeIndexConstants.TriggerType.SCHEDULED, "定时任务");

        private final String code;
        private final String description;

        TriggerType(String code, String description) {
            this.code = code;
            this.description = description;
        }
    }

    /**
     * 扫描任务触发类型。
     */
    @Getter
    public enum ScanTriggerType {

        /**
         * 手动触发。
         */
        MANUAL(CodeIndexConstants.ScanTriggerType.MANUAL, "手动触发"),

        /**
         * Webhook 触发。
         */
        WEBHOOK(CodeIndexConstants.ScanTriggerType.WEBHOOK, "Webhook"),

        /**
         * 发布回调触发。
         */
        DEPLOY_CALLBACK(CodeIndexConstants.ScanTriggerType.DEPLOY_CALLBACK, "发布回调"),

        /**
         * 定时任务触发。
         */
        SCHEDULED(CodeIndexConstants.ScanTriggerType.SCHEDULED, "定时任务");

        private final String code;
        private final String description;

        ScanTriggerType(String code, String description) {
            this.code = code;
            this.description = description;
        }
    }

    /**
     * 定位置信度。
     */
    @Getter
    public enum Confidence {

        /**
         * 高置信度。
         */
        HIGH(CodeIndexConstants.Confidence.HIGH, "高置信度"),

        /**
         * 中置信度。
         */
        MEDIUM(CodeIndexConstants.Confidence.MEDIUM, "中置信度"),

        /**
         * 低置信度。
         */
        LOW(CodeIndexConstants.Confidence.LOW, "低置信度"),

        /**
         * 无置信度。
         */
        NONE(CodeIndexConstants.Confidence.NONE, "无置信度");

        private final String code;
        private final String description;

        Confidence(String code, String description) {
            this.code = code;
            this.description = description;
        }
    }

    /**
     * 定位匹配类型。
     */
    @Getter
    public enum MatchType {

        /**
         * 全限定类名命中。
         */
        QUALIFIED_NAME(CodeIndexConstants.MatchType.QUALIFIED_NAME, "全限定类名命中"),

        /**
         * 文件路径命中。
         */
        FILE_PATH(CodeIndexConstants.MatchType.FILE_PATH, "文件路径命中"),

        /**
         * 简单类名命中。
         */
        SIMPLE_NAME(CodeIndexConstants.MatchType.SIMPLE_NAME, "简单类名命中"),

        /**
         * 包名前缀命中。
         */
        PACKAGE_PREFIX(CodeIndexConstants.MatchType.PACKAGE_PREFIX, "包名前缀命中"),

        /**
         * 兜底策略命中。
         */
        FALLBACK(CodeIndexConstants.MatchType.FALLBACK, "兜底策略命中"),

        /**
         * 未命中。
         */
        NONE(CodeIndexConstants.MatchType.NONE, "未命中");

        private final String code;
        private final String description;

        MatchType(String code, String description) {
            this.code = code;
            this.description = description;
        }
    }

    /**
     * Java 类型。
     */
    @Getter
    public enum ClassKind {

        /**
         * 普通类。
         */
        CLASS(CodeIndexConstants.ClassKind.CLASS, "普通类"),

        /**
         * 接口。
         */
        INTERFACE(CodeIndexConstants.ClassKind.INTERFACE, "接口"),

        /**
         * 枚举。
         */
        ENUM(CodeIndexConstants.ClassKind.ENUM, "枚举"),

        /**
         * 注解。
         */
        ANNOTATION(CodeIndexConstants.ClassKind.ANNOTATION, "注解"),

        /**
         * Record 类型。
         */
        RECORD(CodeIndexConstants.ClassKind.RECORD, "Record 类型");

        private final String code;
        private final String description;

        ClassKind(String code, String description) {
            this.code = code;
            this.description = description;
        }
    }
}
