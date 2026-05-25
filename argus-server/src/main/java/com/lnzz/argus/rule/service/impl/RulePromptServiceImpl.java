package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.rule.dto.res.PromptTemplateSchemeResDTO;
import com.lnzz.argus.rule.service.PromptTemplateService;
import com.lnzz.argus.rule.service.RulePromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @classname: RulePromptServiceImpl
 * @author: Fantasy
 * @date: 2026/05/17 21:29
 * @description: 规则管理 Prompt 服务实现，统一从 Prompt 独立方案表读取评审与错误分析模板。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RulePromptServiceImpl implements RulePromptService {

    private static final String CODE_REVIEW_MAIN = "CODE_REVIEW_MAIN";
    private static final String CODE_REVIEW_JSON_REPAIR = "CODE_REVIEW_JSON_REPAIR";
    private static final String ERROR_ANALYSIS_MAIN = "ERROR_ANALYSIS_MAIN";
    private static final String ERROR_ANALYSIS_JSON_REPAIR = "ERROR_ANALYSIS_JSON_REPAIR";
    private static final String DEFAULT_FOCUS_KEY = "default";

    private final PromptTemplateService promptTemplateService;

    @Override
    public String getTemplateContent(String templateCode, Long scmConfigId) {
        return requireTemplateContent(templateCode, scmConfigId);
    }

    @Override
    public String buildReviewPromptSkeleton(ReviewConfig config, Long scmConfigId) {
        ReviewConfig effectiveConfig = config != null ? config : ReviewConfig.defaults();
        String template = requireTemplateContent(CODE_REVIEW_MAIN, scmConfigId);
        Map<String, String> placeholders = new LinkedHashMap<>();
        ReviewConfig.DimensionsConfig dims = effectiveConfig.getScoring().getDimensions();
        placeholders.put("{{complianceWeight}}", String.valueOf(dims.getCompliance()));
        placeholders.put("{{correctnessWeight}}", String.valueOf(dims.getCorrectness()));
        placeholders.put("{{dataIntegrityWeight}}", String.valueOf(dims.getDataIntegrity()));
        placeholders.put("{{performanceWeight}}", String.valueOf(dims.getPerformance()));
        placeholders.put("{{maintainabilityWeight}}", String.valueOf(dims.getMaintainability()));
        placeholders.put("{{severityDefinitions}}",
                buildSeverityDefinitions(effectiveConfig.getScoring().getSeverityDefinitions()));
        return renderTemplate(template, placeholders);
    }

    @Override
    public String buildReviewJsonRepairPrompt(String originalResponse, Long scmConfigId) {
        return buildJsonRepairPrompt(CODE_REVIEW_JSON_REPAIR, originalResponse, scmConfigId);
    }

    @Override
    public String buildJsonRepairPrompt(String templateCode, String originalResponse, Long scmConfigId) {
        return renderTemplate(requireTemplateContent(templateCode, scmConfigId),
                Map.of("{{originalResponse}}", truncateOriginalResponse(originalResponse)));
    }

    @Override
    public String resolveReviewFocus(String languageTag, ReviewConfig config) {
        ReviewConfig effectiveConfig = config != null ? config : ReviewConfig.defaults();
        String normalizedLanguage = StringUtils.hasText(languageTag)
                ? languageTag.trim().toLowerCase(Locale.ROOT)
                : DEFAULT_FOCUS_KEY;
        Map<String, String> focusByLanguage = effectiveConfig.getRule().getReviewFocus().getFocusByLanguage();
        String focus = focusByLanguage.get(normalizedLanguage);
        if (StringUtils.hasText(focus)) {
            return focus;
        }
        String defaultFocus = focusByLanguage.get(DEFAULT_FOCUS_KEY);
        return StringUtils.hasText(defaultFocus)
                ? defaultFocus
                : "变更是否引入逻辑风险、配置风险、可维护性问题或发布风险";
    }

    @Override
    public String getErrorAnalysisPromptSkeleton(Long scmConfigId) {
        return requireTemplateContent(ERROR_ANALYSIS_MAIN, scmConfigId);
    }

    @Override
    public String buildErrorAnalysisJsonRepairPrompt(String originalResponse, Long scmConfigId) {
        return buildJsonRepairPrompt(ERROR_ANALYSIS_JSON_REPAIR, originalResponse, scmConfigId);
    }

    private String requireTemplateContent(String templateCode, Long scmConfigId) {
        PromptTemplateSchemeResDTO effectiveTemplate = promptTemplateService.getEffectiveTemplate(templateCode, scmConfigId);
        if (effectiveTemplate == null || !StringUtils.hasText(effectiveTemplate.getContentText())) {
            throw new BizException(ResultCode.NOT_FOUND, "未找到可用的 Prompt 模板方案: " + templateCode);
        }
        return effectiveTemplate.getContentText();
    }

    private String buildSeverityDefinitions(Map<String, ReviewConfig.SeverityDefConfig> severityDefinitions) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ReviewConfig.SeverityDefConfig> entry : severityDefinitions.entrySet()) {
            ReviewConfig.SeverityDefConfig def = entry.getValue();
            sb.append("- **").append(entry.getKey()).append("（")
                    .append(def.getLabel()).append("，扣 ")
                    .append(def.getDeduction()).append(" 分）**：");
            List<String> examples = def.getExamples();
            if (examples != null && !examples.isEmpty()) {
                sb.append(String.join("、", examples));
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String renderTemplate(String template, Map<String, String> placeholders) {
        String rendered = template;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }

    private String truncateOriginalResponse(String originalResponse) {
        String compactResponse = originalResponse == null ? "" : originalResponse.trim();
        int maxChars = 16_000;
        if (compactResponse.length() > maxChars) {
            compactResponse = compactResponse.substring(0, maxChars);
        }
        return compactResponse;
    }
}
