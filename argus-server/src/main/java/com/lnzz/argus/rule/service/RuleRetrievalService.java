package com.lnzz.argus.rule.service;

import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.parser.ReviewContext;

/**
 * @classname: RuleRetrievalService
 * @author: Fantasy
 * @date: 2026/05/17 23:58
 * @description: 规则检索服务接口，负责为评审链路输出可直接注入 Prompt 的规范片段。
 */
public interface RuleRetrievalService {

    /**
     * 构建评审上下文对应的规则参考文本。
     *
     * @param context     评审上下文
     * @param reviewConfig 仓库级评审配置
     * @param scmConfigId 当前仓库配置 ID，可为空
     * @return 可直接注入 Prompt 的规则参考文本
     * @author Fantasy
     * @date 2026/05/17 23:58
     */
    String buildRuleReference(ReviewContext context, ReviewConfig reviewConfig, Long scmConfigId);
}
