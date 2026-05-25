package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.rule.dao.entity.PromptTemplateDefinition;
import com.lnzz.argus.rule.dao.mapper.PromptTemplateDefinitionMapper;
import com.lnzz.argus.rule.dto.res.PromptCatalogCategoryResDTO;
import com.lnzz.argus.rule.service.PromptCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @classname: PromptCatalogServiceImpl
 * @author: Fantasy
 * @date: 2026/05/18 22:02
 * @description: Prompt 模板目录服务实现，返回系统预置分类和模板组目录。
 */
@Service
@RequiredArgsConstructor
public class PromptCatalogServiceImpl implements PromptCatalogService {

    private static final Map<String, String> CATEGORY_NAME_MAPPING = Map.of(
            "CODE_REVIEW", "代码评审",
            "ERROR_ANALYSIS", "错误分析",
            "DATA_MONITORING", "数据监控"
    );

    private static final Map<String, String> CATEGORY_DESCRIPTION_MAPPING = Map.of(
            "CODE_REVIEW", "用于 AI 代码评审场景的提示词模板组。",
            "ERROR_ANALYSIS", "用于错误日志分析和根因解释场景的提示词模板组。",
            "DATA_MONITORING", "用于慢 SQL、连接池风险和日志质量等数据监控场景的提示词模板组。"
    );

    private final PromptTemplateDefinitionMapper promptTemplateDefinitionMapper;

    @Override
    public List<PromptCatalogCategoryResDTO> listCatalog(String category, String keyword) {
        List<PromptTemplateDefinition> definitions = promptTemplateDefinitionMapper.selectActiveCatalog(
                normalizeCategory(category), trimToNull(keyword));
        Map<String, PromptCatalogCategoryResDTO> grouped = new LinkedHashMap<>();
        for (PromptTemplateDefinition definition : definitions) {
            PromptCatalogCategoryResDTO categoryDTO = grouped.computeIfAbsent(definition.getCategory(),
                    this::createCategoryDTO);
            categoryDTO.getTemplates().add(toTemplateItem(definition));
        }
        grouped.values().forEach(item -> item.setTemplateCount(item.getTemplates().size()));
        return List.copyOf(grouped.values());
    }

    private PromptCatalogCategoryResDTO createCategoryDTO(String category) {
        PromptCatalogCategoryResDTO dto = new PromptCatalogCategoryResDTO();
        dto.setCategory(category);
        dto.setCategoryName(CATEGORY_NAME_MAPPING.getOrDefault(category, category));
        dto.setDescription(CATEGORY_DESCRIPTION_MAPPING.getOrDefault(category, ""));
        dto.setTemplateCount(0);
        return dto;
    }

    private PromptCatalogCategoryResDTO.PromptCatalogTemplateItemDTO toTemplateItem(PromptTemplateDefinition definition) {
        PromptCatalogCategoryResDTO.PromptCatalogTemplateItemDTO item =
                new PromptCatalogCategoryResDTO.PromptCatalogTemplateItemDTO();
        item.setTemplateCode(definition.getTemplateCode());
        item.setTemplateName(definition.getTemplateName());
        item.setTemplateScene(definition.getTemplateScene());
        item.setSupportScmOverride(definition.getSupportScmOverride());
        item.setSortNo(definition.getSortNo());
        item.setStatus(definition.getStatus());
        item.setDescription(definition.getDescription());
        return item;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
