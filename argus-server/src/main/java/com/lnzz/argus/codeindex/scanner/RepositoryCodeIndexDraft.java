package com.lnzz.argus.codeindex.scanner;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @classname: RepositoryCodeIndexDraft
 * @author: Fantasy
 * @date: 2026/05/19 16:55
 * @description: 仓库源码索引草稿，承载待持久化的模块、类型、包归属和定位映射。
 */
@Data
public class RepositoryCodeIndexDraft implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 模块扫描结果。
     */
    private List<ModuleScanResult> modules = new ArrayList<>();

    /**
     * Java 类型索引。
     */
    private List<JavaFileIndex> classes = new ArrayList<>();

    /**
     * 包归属草稿。
     */
    private List<PackageDraft> packages = new ArrayList<>();

    /**
     * 全限定类名到文件路径的定位映射。
     */
    private Map<String, String> qualifiedNameToFilePath = new LinkedHashMap<>();

    /**
     * 聚合告警。
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 模块数量。
     */
    private Integer moduleCount = 0;

    /**
     * 源码根数量。
     */
    private Integer sourceRootCount = 0;

    /**
     * Java 文件数量。
     */
    private Integer javaFileCount = 0;

    /**
     * Java 类型数量。
     */
    private Integer classCount = 0;

    /**
     * 包数量。
     */
    private Integer packageCount = 0;

    /**
     * 告警数量。
     */
    private Integer warningCount = 0;

    /**
     * 包归属草稿。
     */
    @Data
    public static class PackageDraft implements Serializable {

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
         * 关联模块路径。
         */
        private List<String> modulePaths = new ArrayList<>();

        /**
         * 类型数量。
         */
        private Integer classCount = 0;

        /**
         * 是否 split package。
         */
        private Boolean ambiguous = false;

        /**
         * 包归属置信度。
         */
        private String confidence;
    }
}
