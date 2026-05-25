package com.lnzz.argus.rule.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.rule.dto.req.RuleProfileSaveReqDTO;
import com.lnzz.argus.rule.dto.res.RuleProfileResDTO;
import com.lnzz.argus.rule.service.RuleProfileService;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import com.lnzz.argus.scm.service.ScmReviewConfigSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @classname: RuleProfileServiceImpl
 * @author: Fantasy
 * @date: 2026/05/17 23:58
 * @description: 仓库级规则配置服务实现，基于 ScmConfig.reviewConfig 兼容式读写规则域与评分策略。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleProfileServiceImpl implements RuleProfileService {

    private final ScmConfigService scmConfigService;
    private final ScmReviewConfigSupport scmReviewConfigSupport;

    /**
     * 查询仓库级规则配置。
     *
     * @param scmConfigId SCM 仓库配置 ID
     * @return 规则配置响应
     */
    @Override
    public RuleProfileResDTO getScmRuleProfile(Long scmConfigId) {
        ScmConfig scmConfig = scmConfigService.requireById(scmConfigId);
        ReviewConfig reviewConfig = scmReviewConfigSupport.resolveReviewConfig(scmConfig);
        return toRuleProfileResDTO(scmConfigId, reviewConfig);
    }

    /**
     * 保存仓库级规则配置。
     *
     * @param scmConfigId SCM 仓库配置 ID
     * @param requestDTO  规则配置保存请求
     * @return 保存后的规则配置
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RuleProfileResDTO saveScmRuleProfile(Long scmConfigId, RuleProfileSaveReqDTO requestDTO) {
        if (requestDTO == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则配置保存请求不能为空");
        }
        ScmConfig scmConfig = scmConfigService.requireById(scmConfigId);
        ReviewConfig reviewConfig = scmReviewConfigSupport.resolveReviewConfig(scmConfig);
        log.info("仓库级规则配置保存开始, scmConfigId={}", scmConfigId);
        applyRuleProfile(reviewConfig, requestDTO);
        scmConfig.setReviewConfig(JSON.toJSONString(reviewConfig));
        ScmConfig updated = scmConfigService.saveOrUpdate(scmConfig);
        ReviewConfig refreshed = scmReviewConfigSupport.resolveReviewConfig(updated);
        log.info("仓库级规则配置保存成功, scmConfigId={}", scmConfigId);
        return toRuleProfileResDTO(updated.getId(), refreshed);
    }

    private void applyRuleProfile(ReviewConfig reviewConfig, RuleProfileSaveReqDTO requestDTO) {
        if (requestDTO.getRuleProfile() != null) {
            List<String> categories = normalizeCategories(requestDTO.getRuleProfile().getStandardCategories());
            if (!categories.isEmpty()) {
                reviewConfig.getRule().setStandardCategories(categories);
            }
        }
        if (requestDTO.getScoringProfile() != null) {
            RuleProfileSaveReqDTO.ScoringProfileDTO source = requestDTO.getScoringProfile();
            if (source.getBlockThreshold() != null) {
                reviewConfig.getScoring().setBlockThreshold(source.getBlockThreshold());
            }
            if (source.getBlockingRules() != null) {
                RuleProfileSaveReqDTO.BlockingRulesDTO blockingRules = source.getBlockingRules();
                ReviewConfig.BlockingRuleConfig target = reviewConfig.getScoring().getBlockingRules();
                if (blockingRules.getCriticalDirectBlock() != null) {
                    target.setCriticalDirectBlock(blockingRules.getCriticalDirectBlock());
                }
                target.setMajorBlockThreshold(blockingRules.getMajorBlockThreshold());
                if (blockingRules.getSuggestionOnlyBlockEnabled() != null) {
                    target.setSuggestionOnlyBlockEnabled(blockingRules.getSuggestionOnlyBlockEnabled());
                }
            }
            if (source.getDimensions() != null) {
                RuleProfileSaveReqDTO.DimensionsProfileDTO dimensions = source.getDimensions();
                ReviewConfig.DimensionsConfig target = reviewConfig.getScoring().getDimensions();
                if (dimensions.getCompliance() != null) target.setCompliance(dimensions.getCompliance());
                if (dimensions.getCorrectness() != null) target.setCorrectness(dimensions.getCorrectness());
                if (dimensions.getDataIntegrity() != null) target.setDataIntegrity(dimensions.getDataIntegrity());
                if (dimensions.getPerformance() != null) target.setPerformance(dimensions.getPerformance());
                if (dimensions.getMaintainability() != null) target.setMaintainability(dimensions.getMaintainability());
            }
            if (source.getSeverityDefinitions() != null && !source.getSeverityDefinitions().isEmpty()) {
                Map<String, ReviewConfig.SeverityDefConfig> severityDefinitions = new LinkedHashMap<>();
                source.getSeverityDefinitions().forEach((key, value) -> {
                    if (!StringUtils.hasText(key) || value == null) {
                        return;
                    }
                    ReviewConfig.SeverityDefConfig definition = new ReviewConfig.SeverityDefConfig();
                    definition.setDeduction(value.getDeduction() == null ? 0 : value.getDeduction());
                    definition.setLabel(trimToNull(value.getLabel()));
                    definition.setExamples(value.getExamples() == null ? new ArrayList<>() : value.getExamples());
                    severityDefinitions.put(key.trim().toUpperCase(Locale.ROOT), definition);
                });
                if (!severityDefinitions.isEmpty()) {
                    reviewConfig.getScoring().setSeverityDefinitions(severityDefinitions);
                }
            }
        }
    }
    private RuleProfileResDTO toRuleProfileResDTO(Long scmConfigId, ReviewConfig reviewConfig) {
        RuleProfileResDTO response = new RuleProfileResDTO();
        response.setScmConfigId(scmConfigId);

        RuleProfileResDTO.ScoringProfileDTO scoringProfile = response.getScoringProfile();
        scoringProfile.setBlockThreshold(reviewConfig.getScoring().getBlockThreshold());
        RuleProfileResDTO.BlockingRulesDTO blockingRules = scoringProfile.getBlockingRules();
        blockingRules.setCriticalDirectBlock(reviewConfig.getScoring().getBlockingRules().getCriticalDirectBlock());
        blockingRules.setMajorBlockThreshold(reviewConfig.getScoring().getBlockingRules().getMajorBlockThreshold());
        blockingRules.setSuggestionOnlyBlockEnabled(
                reviewConfig.getScoring().getBlockingRules().getSuggestionOnlyBlockEnabled());
        RuleProfileResDTO.DimensionsProfileDTO dimensions = scoringProfile.getDimensions();
        dimensions.setCompliance(reviewConfig.getScoring().getDimensions().getCompliance());
        dimensions.setCorrectness(reviewConfig.getScoring().getDimensions().getCorrectness());
        dimensions.setDataIntegrity(reviewConfig.getScoring().getDimensions().getDataIntegrity());
        dimensions.setPerformance(reviewConfig.getScoring().getDimensions().getPerformance());
        dimensions.setMaintainability(reviewConfig.getScoring().getDimensions().getMaintainability());

        Map<String, RuleProfileResDTO.SeverityDefinitionDTO> severityDefinitions = new LinkedHashMap<>();
        reviewConfig.getScoring().getSeverityDefinitions().forEach((key, value) -> {
            RuleProfileResDTO.SeverityDefinitionDTO definition = new RuleProfileResDTO.SeverityDefinitionDTO();
            definition.setDeduction(value.getDeduction());
            definition.setLabel(value.getLabel());
            definition.setExamples(value.getExamples() == null ? new ArrayList<>() : value.getExamples());
            severityDefinitions.put(key, definition);
        });
        scoringProfile.setSeverityDefinitions(severityDefinitions);

        response.getRuleProfile().setStandardCategories(
                reviewConfig.getRule().getStandardCategories() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(reviewConfig.getRule().getStandardCategories()));
        return response;
    }

    private List<String> normalizeCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
