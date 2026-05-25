package com.lnzz.argus.rule.dto.res;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @classname: RuleProfileResDTO
 * @author: Fantasy
 * @date: 2026/05/17 22:58
 * @description: 仓库级规则配置响应，返回规则域和评分阈值配置。
 */
@Data
public class RuleProfileResDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * SCM 仓库配置 ID。
     */
    private Long scmConfigId;

    /**
     * 评分阈值与评分规则配置。
     */
    private ScoringProfileDTO scoringProfile = new ScoringProfileDTO();

    /**
     * 规则域配置。
     */
    private RuleProfileDTO ruleProfile = new RuleProfileDTO();

    /**
     * 评分配置。
     */
    @Data
    public static class ScoringProfileDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 阻止合并阈值。
         */
        private Integer blockThreshold;

        /**
         * 五维度权重配置。
         */
        private DimensionsProfileDTO dimensions = new DimensionsProfileDTO();

        /**
         * 阻断规则配置。
         */
        private BlockingRulesDTO blockingRules = new BlockingRulesDTO();

        /**
         * 严重度定义配置。
         */
        private Map<String, SeverityDefinitionDTO> severityDefinitions = new LinkedHashMap<>();
    }

    /**
     * 阻断规则配置。
     */
    @Data
    public static class BlockingRulesDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 出现 CRITICAL 是否直接阻断。
         */
        private Boolean criticalDirectBlock;

        /**
         * MAJOR 问题达到阈值时直接阻断。
         */
        private Integer majorBlockThreshold;

        /**
         * 是否允许仅因建议项触发阻断。
         */
        private Boolean suggestionOnlyBlockEnabled;
    }

    /**
     * 五维度权重配置。
     */
    @Data
    public static class DimensionsProfileDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 规范合规权重。
         */
        private Integer compliance;

        /**
         * 正确性权重。
         */
        private Integer correctness;

        /**
         * 数据完整性权重。
         */
        private Integer dataIntegrity;

        /**
         * 性能权重。
         */
        private Integer performance;

        /**
         * 可维护性权重。
         */
        private Integer maintainability;
    }

    /**
     * 严重度定义。
     */
    @Data
    public static class SeverityDefinitionDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 扣分值。
         */
        private Integer deduction;

        /**
         * 严重度标签。
         */
        private String label;

        /**
         * 示例列表。
         */
        private List<String> examples = new ArrayList<>();
    }

    /**
     * 规则域配置。
     */
    @Data
    public static class RuleProfileDTO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 参与评审的规范分类集合。
         */
        private List<String> standardCategories = new ArrayList<>();
    }
}
