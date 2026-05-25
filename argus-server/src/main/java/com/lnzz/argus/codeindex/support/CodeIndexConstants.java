package com.lnzz.argus.codeindex.support;

/**
 * @classname: CodeIndexConstants
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: 源码索引领域常量，统一约束索引状态、扫描类型、定位置信度和匹配类型。
 */
public final class CodeIndexConstants {

    private CodeIndexConstants() {
    }

    /**
     * 当前源码索引结构版本。
     */
    public static final int CURRENT_INDEX_VERSION = 1;

    /**
     * 默认主分支名称，仅用于未配置分支时的兜底展示。
     */
    public static final String DEFAULT_BRANCH = "main";

    /**
     * 扫描任务常量。
     */
    public static final class ScanTask {

        private ScanTask() {
        }

        /**
         * 任务编号前缀。
         */
        public static final String TASK_NO_PREFIX = "CI-";

        /**
         * 错误信息最大长度。
         */
        public static final int MAX_ERROR_MESSAGE_LENGTH = 1024;

        /**
         * 默认进度更新间隔。
         */
        public static final int DEFAULT_PROGRESS_INTERVAL = 500;

        /**
         * SCM 读取阶段起始进度。
         */
        public static final int SCM_READING_PROGRESS_START = 0;

        /**
         * SCM 读取阶段结束进度。
         */
        public static final int SCM_READING_PROGRESS_END = 20;

        /**
         * 模块扫描和源码根发现阶段起始进度。
         */
        public static final int MODULE_SCANNING_PROGRESS_START = 20;

        /**
         * 模块扫描和源码根发现阶段结束进度。
         */
        public static final int MODULE_SCANNING_PROGRESS_END = 30;

        /**
         * Java 解析阶段起始进度。
         */
        public static final int JAVA_PARSING_PROGRESS_START = 30;

        /**
         * Java 解析阶段结束进度。
         */
        public static final int JAVA_PARSING_PROGRESS_END = 80;

        /**
         * 索引聚合阶段起始进度。
         */
        public static final int INDEX_AGGREGATING_PROGRESS_START = 80;

        /**
         * 索引聚合阶段结束进度。
         */
        public static final int INDEX_AGGREGATING_PROGRESS_END = 85;

        /**
         * 索引持久化阶段起始进度。
         */
        public static final int INDEX_PERSISTING_PROGRESS_START = 85;

        /**
         * 索引持久化阶段结束进度。
         */
        public static final int INDEX_PERSISTING_PROGRESS_END = 98;

        /**
         * 完成进度。
         */
        public static final int COMPLETED_PROGRESS = 100;
    }

    /**
     * 扫描状态常量。
     */
    public static final class ScanStatus {

        private ScanStatus() {
        }

        /**
         * 等待扫描。
         */
        public static final String PENDING = "PENDING";

        /**
         * 扫描中。
         */
        public static final String RUNNING = "RUNNING";

        /**
         * 部分成功。
         */
        public static final String PARTIAL = "PARTIAL";

        /**
         * 扫描成功。
         */
        public static final String SUCCESS = "SUCCESS";

        /**
         * 扫描失败。
         */
        public static final String FAILED = "FAILED";
    }

    /**
     * 扫描任务状态常量。
     */
    public static final class ScanTaskStatus {

        private ScanTaskStatus() {
        }

        /**
         * 已创建，等待执行。
         */
        public static final String PENDING = "PENDING";

        /**
         * 正在执行。
         */
        public static final String RUNNING = "RUNNING";

        /**
         * 扫描成功并写入索引。
         */
        public static final String SUCCESS = "SUCCESS";

        /**
         * 扫描失败。
         */
        public static final String FAILED = "FAILED";

        /**
         * 已取消，首版仅预留。
         */
        public static final String CANCELED = "CANCELED";

        /**
         * 普通刷新命中已有成功索引。
         */
        public static final String REUSED = "REUSED";
    }

    /**
     * 扫描阶段常量。
     */
    public static final class ScanStage {

        private ScanStage() {
        }

        /**
         * 等待执行。
         */
        public static final String WAITING = "WAITING";

        /**
         * 读取 SCM 文件或 archive。
         */
        public static final String SCM_READING = "SCM_READING";

        /**
         * 扫描 Maven 模块。
         */
        public static final String MODULE_SCANNING = "MODULE_SCANNING";

        /**
         * 发现源码根。
         */
        public static final String SOURCE_ROOT_DISCOVERING = "SOURCE_ROOT_DISCOVERING";

        /**
         * 解析 Java 文件。
         */
        public static final String JAVA_PARSING = "JAVA_PARSING";

        /**
         * 聚合 class/package 索引。
         */
        public static final String INDEX_AGGREGATING = "INDEX_AGGREGATING";

        /**
         * 持久化索引。
         */
        public static final String INDEX_PERSISTING = "INDEX_PERSISTING";

        /**
         * 完成。
         */
        public static final String COMPLETED = "COMPLETED";

        /**
         * 失败。
         */
        public static final String FAILED = "FAILED";
    }

    /**
     * 扫描类型常量。
     */
    public static final class ScanType {

        private ScanType() {
        }

        /**
         * 全量扫描。
         */
        public static final String FULL = "FULL";

        /**
         * 增量扫描。
         */
        public static final String INCREMENTAL = "INCREMENTAL";

        /**
         * 模块级重扫。
         */
        public static final String MODULE_RESCAN = "MODULE_RESCAN";

        /**
         * 强制重建索引。
         */
        public static final String REBUILD = "REBUILD";
    }

    /**
     * 触发类型常量。
     */
    public static final class TriggerType {

        private TriggerType() {
        }

        /**
         * 首次初始化触发。
         */
        public static final String FIRST_INIT = "FIRST_INIT";

        /**
         * Webhook 触发。
         */
        public static final String WEBHOOK = "WEBHOOK";

        /**
         * 手动触发。
         */
        public static final String MANUAL = "MANUAL";

        /**
         * 发布回调触发。
         */
        public static final String DEPLOY_CALLBACK = "DEPLOY_CALLBACK";

        /**
         * 定时任务触发。
         */
        public static final String SCHEDULED = "SCHEDULED";
    }

    /**
     * 扫描任务触发类型常量。
     */
    public static final class ScanTriggerType {

        private ScanTriggerType() {
        }

        /**
         * 手动触发。
         */
        public static final String MANUAL = "MANUAL";

        /**
         * Webhook 触发。
         */
        public static final String WEBHOOK = "WEBHOOK";

        /**
         * 发布回调触发。
         */
        public static final String DEPLOY_CALLBACK = "DEPLOY_CALLBACK";

        /**
         * 定时任务触发。
         */
        public static final String SCHEDULED = "SCHEDULED";
    }

    /**
     * 置信度常量。
     */
    public static final class Confidence {

        private Confidence() {
        }

        /**
         * 高置信度。
         */
        public static final String HIGH = "HIGH";

        /**
         * 中置信度。
         */
        public static final String MEDIUM = "MEDIUM";

        /**
         * 低置信度。
         */
        public static final String LOW = "LOW";

        /**
         * 无有效置信度。
         */
        public static final String NONE = "NONE";
    }

    /**
     * 定位匹配类型常量。
     */
    public static final class MatchType {

        private MatchType() {
        }

        /**
         * 全限定类名命中。
         */
        public static final String QUALIFIED_NAME = "QUALIFIED_NAME";

        /**
         * 文件路径命中。
         */
        public static final String FILE_PATH = "FILE_PATH";

        /**
         * 简单类名命中。
         */
        public static final String SIMPLE_NAME = "SIMPLE_NAME";

        /**
         * 包名前缀命中。
         */
        public static final String PACKAGE_PREFIX = "PACKAGE_PREFIX";

        /**
         * 兜底策略命中。
         */
        public static final String FALLBACK = "FALLBACK";

        /**
         * 未命中。
         */
        public static final String NONE = "NONE";
    }

    /**
     * Java 类型常量。
     */
    public static final class ClassKind {

        private ClassKind() {
        }

        /**
         * 普通类。
         */
        public static final String CLASS = "CLASS";

        /**
         * 接口。
         */
        public static final String INTERFACE = "INTERFACE";

        /**
         * 枚举。
         */
        public static final String ENUM = "ENUM";

        /**
         * 注解。
         */
        public static final String ANNOTATION = "ANNOTATION";

        /**
         * Record 类型。
         */
        public static final String RECORD = "RECORD";
    }
}
