package com.lnzz.argus.codeindex.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @classname: SourceLocateResDTO
 * @author: Fantasy
 * @date: 2026/05/19 16:40
 * @description: 源码定位响应，表达定位命中结果、置信度、匹配类型、告警和候选结果。
 */
@Data
public class SourceLocateResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 是否命中源码位置。
     */
    private Boolean matched;

    /**
     * 定位置信度：HIGH/MEDIUM/LOW/NONE。
     */
    private String confidence;

    /**
     * 匹配类型：QUALIFIED_NAME/FILE_PATH/SIMPLE_NAME/PACKAGE_PREFIX/FALLBACK/NONE。
     */
    private String matchType;

    /**
     * 源码索引 ID。
     */
    private Long indexId;

    /**
     * 提交号。
     */
    private String commitSha;

    /**
     * 模块路径。
     */
    private String modulePath;

    /**
     * 源码根路径。
     */
    private String sourceRoot;

    /**
     * 文件路径。
     */
    private String filePath;

    /**
     * 包名。
     */
    private String packageName;

    /**
     * 简单类名。
     */
    private String className;

    /**
     * 全限定类名。
     */
    private String qualifiedName;

    /**
     * 目标行号。
     */
    private Integer lineNumber;

    /**
     * 定位告警。
     */
    private List<String> warnings = new ArrayList<>();

    /**
     * 候选结果列表。
     */
    private List<CandidateDTO> candidates = new ArrayList<>();

    /**
     * 源码定位候选结果。
     */
    @Data
    public static class CandidateDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 源码索引 ID。
         */
        private Long indexId;

        /**
         * 提交号。
         */
        private String commitSha;

        /**
         * 模块路径。
         */
        private String modulePath;

        /**
         * 源码根路径。
         */
        private String sourceRoot;

        /**
         * 文件路径。
         */
        private String filePath;

        /**
         * 包名。
         */
        private String packageName;

        /**
         * 简单类名。
         */
        private String className;

        /**
         * 全限定类名。
         */
        private String qualifiedName;

        /**
         * 置信度。
         */
        private String confidence;

        /**
         * 匹配类型。
         */
        private String matchType;
    }
}
