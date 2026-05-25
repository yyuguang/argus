package com.lnzz.argus.rule.service;

import com.lnzz.argus.rule.dto.req.RuleProfileSaveReqDTO;
import com.lnzz.argus.rule.dto.res.RuleProfileResDTO;

/**
 * @classname: RuleProfileService
 * @author: Fantasy
 * @date: 2026/05/17 23:58
 * @description: 仓库级规则配置服务接口，负责规则域、Prompt 模板和评分阈值配置读写。
 */
public interface RuleProfileService {

    /**
     * 查询仓库级规则配置。
     *
     * @param scmConfigId SCM 仓库配置 ID
     * @return 规则配置响应
     * @author Fantasy
     * @date 2026/05/17 23:58
     */
    RuleProfileResDTO getScmRuleProfile(Long scmConfigId);

    /**
     * 保存仓库级规则配置。
     *
     * @param scmConfigId SCM 仓库配置 ID
     * @param requestDTO  规则配置保存请求
     * @return 保存后的规则配置
     * @author Fantasy
     * @date 2026/05/17 23:58
     */
    RuleProfileResDTO saveScmRuleProfile(Long scmConfigId, RuleProfileSaveReqDTO requestDTO);
}
