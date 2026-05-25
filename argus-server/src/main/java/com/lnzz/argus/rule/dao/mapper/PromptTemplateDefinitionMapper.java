package com.lnzz.argus.rule.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.common.constant.SystemDataConstants;
import com.lnzz.argus.rule.dao.entity.PromptTemplateDefinition;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @classname: PromptTemplateDefinitionMapper
 * @author: Fantasy
 * @date: 2026/05/18 11:10
 * @description: Prompt 模板定义 Mapper，封装目录查询和模板定义基础访问语义。
 */
@Mapper
public interface PromptTemplateDefinitionMapper extends BaseMapper<PromptTemplateDefinition> {

    /**
     * 查询启用中的 Prompt 模板目录，支持按分类和关键字筛选。
     *
     * @param category 一级分类，可为空
     * @param keyword  模板编码、名称或描述关键字，可为空
     * @return Prompt 模板定义列表
     * @author Fantasy
     * @date 2026/05/18 11:10
     */
    default List<PromptTemplateDefinition> selectActiveCatalog(String category, String keyword) {
        return selectList(new LambdaQueryWrapper<PromptTemplateDefinition>()
                .eq(PromptTemplateDefinition::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(PromptTemplateDefinition::getStatus, "ACTIVE")
                .eq(hasText(category), PromptTemplateDefinition::getCategory, category)
                .and(hasText(keyword), wrapper -> wrapper
                        .like(PromptTemplateDefinition::getTemplateCode, keyword)
                        .or()
                        .like(PromptTemplateDefinition::getTemplateName, keyword)
                        .or()
                        .like(PromptTemplateDefinition::getDescription, keyword))
                .orderByAsc(PromptTemplateDefinition::getSortNo)
                .orderByAsc(PromptTemplateDefinition::getId));
    }

    /**
     * 按模板编码查询启用中的模板定义。
     *
     * @param templateCode 模板编码
     * @return Prompt 模板定义；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/18 11:10
     */
    default PromptTemplateDefinition selectActiveByTemplateCode(String templateCode) {
        if (!hasText(templateCode)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<PromptTemplateDefinition>()
                .eq(PromptTemplateDefinition::getTemplateCode, templateCode)
                .eq(PromptTemplateDefinition::getStatus, "ACTIVE")
                .eq(PromptTemplateDefinition::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
