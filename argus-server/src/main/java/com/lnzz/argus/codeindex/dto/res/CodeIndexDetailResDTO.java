package com.lnzz.argus.codeindex.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @classname: CodeIndexDetailResDTO
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: 源码索引详情响应，聚合索引摘要、模块统计、包归属统计和扫描告警。
 */
@Data
public class CodeIndexDetailResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 索引摘要。
     */
    private CodeIndexSummaryResDTO indexSummary;

    /**
     * 模块统计列表。
     */
    private List<ModuleSummaryDTO> modules = new ArrayList<>();

    /**
     * 包归属统计列表。
     */
    private List<PackageSummaryDTO> packages = new ArrayList<>();

    /**
     * 扫描告警列表。
     */
    private List<ScanWarningDTO> warnings = new ArrayList<>();

    /**
     * 模块统计。
     */
    @Data
    public static class ModuleSummaryDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 模块索引 ID。
         */
        private Long moduleId;

        /**
         * 模块名称。
         */
        private String moduleName;

        /**
         * 模块路径。
         */
        private String modulePath;

        /**
         * 父模块路径。
         */
        private String parentModulePath;

        /**
         * 构建类型，如 maven/gradle/plain。
         */
        private String buildType;

        /**
         * 打包类型。
         */
        private String packaging;

        /**
         * 源码根 JSON。
         */
        private String sourceRootsJson;

        /**
         * Java 文件数量。
         */
        private Integer javaFileCount;

        /**
         * Java 类型数量。
         */
        private Integer classCount;

        /**
         * 扫描状态。
         */
        private String scanStatus;

        /**
         * 告警信息。
         */
        private String warningMessage;
    }

    /**
     * 包归属统计。
     */
    @Data
    public static class PackageSummaryDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 包名。
         */
        private String packageName;

        /**
         * 主模块路径。
         */
        private String primaryModulePath;

        /**
         * 关联模块路径 JSON。
         */
        private String modulePathsJson;

        /**
         * 类型数量。
         */
        private Integer classCount;

        /**
         * 是否存在多模块歧义。
         */
        private Boolean ambiguous;

        /**
         * 置信度。
         */
        private String confidence;
    }

    /**
     * 扫描告警。
     */
    @Data
    public static class ScanWarningDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 告警级别。
         */
        private String level;

        /**
         * 告警信息。
         */
        private String message;

        /**
         * 模块路径。
         */
        private String modulePath;

        /**
         * 文件路径。
         */
        private String filePath;
    }
}
