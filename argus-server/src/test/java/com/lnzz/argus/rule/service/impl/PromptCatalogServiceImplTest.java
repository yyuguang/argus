package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.rule.dao.entity.PromptTemplateDefinition;
import com.lnzz.argus.rule.dao.mapper.PromptTemplateDefinitionMapper;
import com.lnzz.argus.rule.dto.res.PromptCatalogCategoryResDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromptCatalogServiceImpl - Prompt 模板目录服务")
class PromptCatalogServiceImplTest {

    @Mock
    private PromptTemplateDefinitionMapper promptTemplateDefinitionMapper;

    private PromptCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PromptCatalogServiceImpl(promptTemplateDefinitionMapper);
    }

    @Test
    @DisplayName("查询目录时应正确返回数据监控分类名称和模板数量")
    void listCatalogShouldGroupDataMonitoringTemplates() {
        when(promptTemplateDefinitionMapper.selectActiveCatalog("DATA_MONITORING", null))
                .thenReturn(List.of(
                        createDefinition("SLOW_SQL_ANALYSIS_MAIN", "慢 SQL 分析主提示词", "DATA_MONITORING", "MAIN", 50),
                        createDefinition("DB_POOL_RISK_ANALYSIS_MAIN", "连接池风险分析主提示词", "DATA_MONITORING", "MAIN", 70)
                ));

        List<PromptCatalogCategoryResDTO> result = service.listCatalog("DATA_MONITORING", null);

        assertEquals(1, result.size());
        PromptCatalogCategoryResDTO category = result.get(0);
        assertEquals("DATA_MONITORING", category.getCategory());
        assertEquals("数据监控", category.getCategoryName());
        assertEquals(2, category.getTemplateCount());
        assertEquals(2, category.getTemplates().size());
        assertEquals("SLOW_SQL_ANALYSIS_MAIN", category.getTemplates().get(0).getTemplateCode());
        assertEquals("DB_POOL_RISK_ANALYSIS_MAIN", category.getTemplates().get(1).getTemplateCode());
    }

    private PromptTemplateDefinition createDefinition(String templateCode,
                                                      String templateName,
                                                      String category,
                                                      String templateScene,
                                                      int sortNo) {
        PromptTemplateDefinition definition = new PromptTemplateDefinition();
        definition.setTemplateCode(templateCode);
        definition.setTemplateName(templateName);
        definition.setCategory(category);
        definition.setTemplateScene(templateScene);
        definition.setSupportScmOverride(true);
        definition.setSortNo(sortNo);
        definition.setStatus("ACTIVE");
        definition.setDescription(templateName);
        return definition;
    }
}
