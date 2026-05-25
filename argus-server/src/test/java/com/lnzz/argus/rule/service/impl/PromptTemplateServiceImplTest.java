package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.rule.dao.entity.PromptTemplateDefinition;
import com.lnzz.argus.rule.dao.entity.PromptTemplateScheme;
import com.lnzz.argus.rule.dao.mapper.PromptTemplateDefinitionMapper;
import com.lnzz.argus.rule.dao.mapper.PromptTemplateSchemeMapper;
import com.lnzz.argus.rule.dto.req.PromptTemplateSaveReqDTO;
import com.lnzz.argus.rule.dto.res.PromptTemplateSchemeResDTO;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromptTemplateServiceImpl - Prompt 模板方案服务")
class PromptTemplateServiceImplTest {

    @Mock
    private PromptTemplateDefinitionMapper promptTemplateDefinitionMapper;

    @Mock
    private PromptTemplateSchemeMapper promptTemplateSchemeMapper;

    @Mock
    private ScmConfigService scmConfigService;

    private PromptTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromptTemplateServiceImpl(
                promptTemplateDefinitionMapper,
                promptTemplateSchemeMapper,
                scmConfigService);
    }

    @Test
    @DisplayName("查询仓库级方案时无覆盖则回落到全局兜底")
    void listScmSchemesFallsBackToGlobalWhenNoOverride() {
        PromptTemplateDefinition definition = createDefinition("CODE_REVIEW_MAIN", "CODE_REVIEW", true);
        PromptTemplateScheme globalScheme = createScheme(1L, "CODE_REVIEW_MAIN", "GLOBAL", 0L, "global-content", "ACTIVE");
        when(scmConfigService.requireById(5L)).thenReturn(new ScmConfig());
        when(promptTemplateDefinitionMapper.selectActiveCatalog(null, null)).thenReturn(List.of(definition));
        when(promptTemplateSchemeMapper.selectActiveGlobalSchemes()).thenReturn(List.of(globalScheme));
        when(promptTemplateSchemeMapper.selectActiveScmOverrides(5L)).thenReturn(List.of());

        List<PromptTemplateSchemeResDTO> responses = service.listScmSchemes(5L, null, null);

        assertEquals(1, responses.size());
        PromptTemplateSchemeResDTO response = responses.get(0);
        assertEquals("SCM", response.getCurrentScope());
        assertEquals(5L, response.getCurrentScmConfigId());
        assertFalse(Boolean.TRUE.equals(response.getHasScmOverride()));
        assertEquals("GLOBAL", response.getEffectiveScope());
        assertEquals(0L, response.getEffectiveScmConfigId());
        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    @DisplayName("保存仓库级方案时会复用已存在的禁用覆盖记录")
    void saveScmSchemeReusesDisabledOverride() {
        PromptTemplateDefinition definition = createDefinition("CODE_REVIEW_MAIN", "CODE_REVIEW", true);
        PromptTemplateScheme existing = createScheme(11L, "CODE_REVIEW_MAIN", "SCM", 8L, "old-content", "DISABLED");
        PromptTemplateSaveReqDTO requestDTO = new PromptTemplateSaveReqDTO();
        requestDTO.setContentText("  new-content  ");
        requestDTO.setRemark("订单仓库覆盖");

        when(promptTemplateDefinitionMapper.selectActiveByTemplateCode("CODE_REVIEW_MAIN")).thenReturn(definition);
        when(scmConfigService.requireById(8L)).thenReturn(new ScmConfig());
        when(promptTemplateSchemeMapper.selectAnyByTemplateCode("CODE_REVIEW_MAIN", "SCM", 8L)).thenReturn(existing);
        when(promptTemplateSchemeMapper.selectActiveByTemplateCode("CODE_REVIEW_MAIN", "SCM", 8L)).thenReturn(existing);
        when(promptTemplateSchemeMapper.selectActiveByTemplateCode("CODE_REVIEW_MAIN", "GLOBAL", 0L)).thenReturn(null);

        PromptTemplateSchemeResDTO response = service.saveScmScheme(8L, "CODE_REVIEW_MAIN", requestDTO);

        assertEquals("SCM", response.getCurrentScope());
        assertEquals("SCM", response.getEffectiveScope());
        assertEquals("new-content", response.getContentText());
        assertEquals("ACTIVE", existing.getStatus());
        assertEquals("new-content", existing.getContentText());
        verify(promptTemplateSchemeMapper).updateById(existing);
        verify(promptTemplateSchemeMapper, never()).insert(any(PromptTemplateScheme.class));
    }

    private PromptTemplateDefinition createDefinition(String templateCode, String category, boolean supportScmOverride) {
        PromptTemplateDefinition definition = new PromptTemplateDefinition();
        definition.setTemplateCode(templateCode);
        definition.setTemplateName(templateCode);
        definition.setCategory(category);
        definition.setTemplateScene("MAIN");
        definition.setSupportScmOverride(supportScmOverride);
        definition.setStatus("ACTIVE");
        return definition;
    }

    private PromptTemplateScheme createScheme(Long id,
                                              String templateCode,
                                              String scope,
                                              Long scmConfigId,
                                              String contentText,
                                              String status) {
        PromptTemplateScheme scheme = new PromptTemplateScheme();
        scheme.setId(id);
        scheme.setTemplateCode(templateCode);
        scheme.setScope(scope);
        scheme.setScmConfigId(scmConfigId);
        scheme.setContentText(contentText);
        scheme.setStatus(status);
        scheme.setRemark("remark");
        return scheme;
    }
}
