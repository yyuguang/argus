package com.lnzz.argus.rule.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.rule.dto.req.RuleProfileSaveReqDTO;
import com.lnzz.argus.rule.dto.res.RuleProfileResDTO;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import com.lnzz.argus.scm.service.ScmReviewConfigSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RuleProfileServiceImpl - 仓库级规则配置")
class RuleProfileServiceImplTest {

    @Mock
    private ScmConfigService scmConfigService;

    private RuleProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleProfileServiceImpl(scmConfigService, new ScmReviewConfigSupport());
    }

    @Test
    @DisplayName("查询规则配置时返回解析后的规则域和评分域")
    void getScmRuleProfileReturnsResolvedProfile() {
        ScmConfig scmConfig = createScmConfig(9L, ReviewConfig.defaults());
        when(scmConfigService.requireById(9L)).thenReturn(scmConfig);

        RuleProfileResDTO response = service.getScmRuleProfile(9L);

        assertEquals(9L, response.getScmConfigId());
        assertEquals(60, response.getScoringProfile().getBlockThreshold());
        assertEquals(true, response.getScoringProfile().getBlockingRules().getCriticalDirectBlock());
        assertEquals(25, response.getScoringProfile().getDimensions().getCompliance());
        assertEquals(List.of("CODING", "API", "DATABASE", "SECURITY", "CUSTOM"),
                response.getRuleProfile().getStandardCategories());
    }

    @Test
    @DisplayName("保存规则配置时会规范化分类、语言键和严重度定义")
    void saveScmRuleProfileNormalizesAndPersistsReviewConfig() {
        ReviewConfig reviewConfig = ReviewConfig.defaults();
        ScmConfig existing = createScmConfig(10L, reviewConfig);
        when(scmConfigService.requireById(10L)).thenReturn(existing);
        when(scmConfigService.saveOrUpdate(any(ScmConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RuleProfileSaveReqDTO requestDTO = new RuleProfileSaveReqDTO();
        requestDTO.getRuleProfile().setStandardCategories(List.of("coding", " api ", "coding"));
        requestDTO.getScoringProfile().setBlockThreshold(70);
        requestDTO.getScoringProfile().getBlockingRules().setCriticalDirectBlock(false);
        requestDTO.getScoringProfile().getBlockingRules().setMajorBlockThreshold(2);
        requestDTO.getScoringProfile().getBlockingRules().setSuggestionOnlyBlockEnabled(true);
        requestDTO.getScoringProfile().getDimensions().setCompliance(35);
        RuleProfileSaveReqDTO.SeverityDefinitionDTO severity = new RuleProfileSaveReqDTO.SeverityDefinitionDTO();
        severity.setDeduction(9);
        severity.setLabel(" 严重 ");
        severity.setExamples(List.of("case-a", "case-b"));
        requestDTO.getScoringProfile().getSeverityDefinitions().put(" major ", severity);

        RuleProfileResDTO response = service.saveScmRuleProfile(10L, requestDTO);

        assertEquals(10L, response.getScmConfigId());
        assertEquals(List.of("CODING", "API"), response.getRuleProfile().getStandardCategories());
        assertEquals(70, response.getScoringProfile().getBlockThreshold());
        assertEquals(false, response.getScoringProfile().getBlockingRules().getCriticalDirectBlock());
        assertEquals(2, response.getScoringProfile().getBlockingRules().getMajorBlockThreshold());
        assertEquals(true, response.getScoringProfile().getBlockingRules().getSuggestionOnlyBlockEnabled());
        assertEquals(35, response.getScoringProfile().getDimensions().getCompliance());
        assertEquals(9, response.getScoringProfile().getSeverityDefinitions().get("MAJOR").getDeduction());
        assertEquals("严重", response.getScoringProfile().getSeverityDefinitions().get("MAJOR").getLabel());

        ArgumentCaptor<ScmConfig> captor = ArgumentCaptor.forClass(ScmConfig.class);
        verify(scmConfigService).saveOrUpdate(captor.capture());
        ReviewConfig persisted = JSON.parseObject(captor.getValue().getReviewConfig(), ReviewConfig.class);
        assertEquals(List.of("CODING", "API"), persisted.getRule().getStandardCategories());
        assertEquals(9, persisted.getScoring().getSeverityDefinitions().get("MAJOR").getDeduction());
        assertEquals(false, persisted.getScoring().getBlockingRules().getCriticalDirectBlock());
        assertEquals(2, persisted.getScoring().getBlockingRules().getMajorBlockThreshold());
        assertEquals(true, persisted.getScoring().getBlockingRules().getSuggestionOnlyBlockEnabled());
    }

    @Test
    @DisplayName("保存规则配置时请求不能为空")
    void saveScmRuleProfileRejectsNullRequest() {
        BizException exception = assertThrows(BizException.class,
                () -> service.saveScmRuleProfile(12L, null));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
        assertEquals("规则配置保存请求不能为空", exception.getMessage());
    }

    private ScmConfig createScmConfig(Long id, ReviewConfig reviewConfig) {
        ScmConfig scmConfig = new ScmConfig();
        scmConfig.setId(id);
        scmConfig.setScmProvider("github");
        scmConfig.setReviewConfig(JSON.toJSONString(reviewConfig));
        return scmConfig;
    }
}
