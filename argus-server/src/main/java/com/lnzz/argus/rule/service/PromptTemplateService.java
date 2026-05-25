package com.lnzz.argus.rule.service;

import com.lnzz.argus.rule.dto.req.PromptTemplateSaveReqDTO;
import com.lnzz.argus.rule.dto.res.PromptTemplateSchemeResDTO;

import java.util.List;

/**
 * @classname: PromptTemplateService
 * @author: Fantasy
 * @date: 2026/05/18 22:02
 * @description: Prompt 模板方案服务接口，负责全局兜底方案、仓库级覆盖方案和最终生效方案管理。
 */
public interface PromptTemplateService {

    /**
     * 查询全局 Prompt 方案列表。
     *
     * @param category 一级分类，可为空
     * @param keyword  模板关键字，可为空
     * @return 全局 Prompt 方案列表
     */
    List<PromptTemplateSchemeResDTO> listGlobalSchemes(String category, String keyword);

    /**
     * 查询仓库级 Prompt 方案列表。
     *
     * @param scmConfigId SCM 仓库配置 ID
     * @param category    一级分类，可为空
     * @param keyword     模板关键字，可为空
     * @return 仓库级 Prompt 方案列表
     */
    List<PromptTemplateSchemeResDTO> listScmSchemes(Long scmConfigId, String category, String keyword);

    /**
     * 查询指定模板组在某作用域下的详情。
     *
     * @param templateCode 模板编码
     * @param scope        作用域
     * @param scmConfigId  SCM 仓库配置 ID，可为空
     * @return Prompt 模板详情
     */
    PromptTemplateSchemeResDTO getTemplateDetail(String templateCode, String scope, Long scmConfigId);

    /**
     * 查询指定模板组的最终生效方案。
     *
     * @param templateCode 模板编码
     * @param scmConfigId  SCM 仓库配置 ID，可为空
     * @return 最终生效方案
     */
    PromptTemplateSchemeResDTO getEffectiveTemplate(String templateCode, Long scmConfigId);

    /**
     * 保存全局 Prompt 模板方案。
     *
     * @param templateCode 模板编码
     * @param requestDTO   保存请求
     * @return 保存后的方案详情
     */
    PromptTemplateSchemeResDTO saveGlobalScheme(String templateCode, PromptTemplateSaveReqDTO requestDTO);

    /**
     * 保存仓库级 Prompt 覆盖方案。
     *
     * @param scmConfigId  SCM 仓库配置 ID
     * @param templateCode 模板编码
     * @param requestDTO   保存请求
     * @return 保存后的方案详情
     */
    PromptTemplateSchemeResDTO saveScmScheme(Long scmConfigId, String templateCode, PromptTemplateSaveReqDTO requestDTO);

    /**
     * 删除仓库级 Prompt 覆盖方案。
     *
     * @param scmConfigId  SCM 仓库配置 ID
     * @param templateCode 模板编码
     */
    void deleteScmOverride(Long scmConfigId, String templateCode);
}
