package com.lnzz.argus.rule.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.common.constant.SystemDataConstants;
import com.lnzz.argus.rule.dao.entity.PromptTemplateScheme;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @classname: PromptTemplateSchemeMapper
 * @author: Fantasy
 * @date: 2026/05/18 11:10
 * @description: Prompt 模板方案 Mapper，封装全局方案、仓库覆盖方案和生效方案查询基础语义。
 */
@Mapper
public interface PromptTemplateSchemeMapper extends BaseMapper<PromptTemplateScheme> {

    /**
     * 查询指定作用域下的全部启用方案。
     *
     * @param scope       作用域：GLOBAL / SCM
     * @param scmConfigId 仓库配置 ID，GLOBAL 固定传 0
     * @return Prompt 模板方案列表
     * @author Fantasy
     * @date 2026/05/18 11:10
     */
    default List<PromptTemplateScheme> selectActiveByScope(String scope, Long scmConfigId) {
        if (!hasText(scope) || scmConfigId == null) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapper<PromptTemplateScheme>()
                .eq(PromptTemplateScheme::getScope, scope)
                .eq(PromptTemplateScheme::getScmConfigId, scmConfigId)
                .eq(PromptTemplateScheme::getStatus, "ACTIVE")
                .eq(PromptTemplateScheme::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByAsc(PromptTemplateScheme::getTemplateCode)
                .orderByDesc(PromptTemplateScheme::getUpdateTime)
                .orderByDesc(PromptTemplateScheme::getId));
    }

    /**
     * 按模板编码和作用域查询启用方案。
     *
     * @param templateCode 模板编码
     * @param scope        作用域：GLOBAL / SCM
     * @param scmConfigId  仓库配置 ID，GLOBAL 固定传 0
     * @return Prompt 模板方案；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/18 11:10
     */
    default PromptTemplateScheme selectActiveByTemplateCode(String templateCode, String scope, Long scmConfigId) {
        if (!hasText(templateCode) || !hasText(scope) || scmConfigId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<PromptTemplateScheme>()
                .eq(PromptTemplateScheme::getTemplateCode, templateCode)
                .eq(PromptTemplateScheme::getScope, scope)
                .eq(PromptTemplateScheme::getScmConfigId, scmConfigId)
                .eq(PromptTemplateScheme::getStatus, "ACTIVE")
                .eq(PromptTemplateScheme::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByDesc(PromptTemplateScheme::getUpdateTime)
                .orderByDesc(PromptTemplateScheme::getId)
                .last("limit 1"));
    }

    /**
     * 按模板编码和作用域查询方案，不限制状态，供保存与恢复覆盖场景复用。
     *
     * @param templateCode 模板编码
     * @param scope        作用域：GLOBAL / SCM
     * @param scmConfigId  仓库配置 ID，GLOBAL 固定传 0
     * @return Prompt 模板方案；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/18 22:02
     */
    default PromptTemplateScheme selectAnyByTemplateCode(String templateCode, String scope, Long scmConfigId) {
        if (!hasText(templateCode) || !hasText(scope) || scmConfigId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<PromptTemplateScheme>()
                .eq(PromptTemplateScheme::getTemplateCode, templateCode)
                .eq(PromptTemplateScheme::getScope, scope)
                .eq(PromptTemplateScheme::getScmConfigId, scmConfigId)
                .eq(PromptTemplateScheme::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByDesc(PromptTemplateScheme::getUpdateTime)
                .orderByDesc(PromptTemplateScheme::getId)
                .last("limit 1"));
    }

    /**
     * 查询某仓库下的全部覆盖方案。
     *
     * @param scmConfigId 仓库配置 ID
     * @return 仓库级 Prompt 模板方案列表
     * @author Fantasy
     * @date 2026/05/18 11:10
     */
    default List<PromptTemplateScheme> selectActiveScmOverrides(Long scmConfigId) {
        if (scmConfigId == null) {
            return List.of();
        }
        return selectActiveByScope("SCM", scmConfigId);
    }

    /**
     * 查询全部全局兜底方案。
     *
     * @return 全局 Prompt 模板方案列表
     * @author Fantasy
     * @date 2026/05/18 11:10
     */
    default List<PromptTemplateScheme> selectActiveGlobalSchemes() {
        return selectActiveByScope("GLOBAL", 0L);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
