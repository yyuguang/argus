package com.lnzz.argus.rule.service;

import com.lnzz.argus.review.config.ReviewConfig;

/**
 * @classname: RulePromptService
 * @author: Fantasy
 * @date: 2026/05/17 21:29
 * @description: 规则管理 Prompt 服务接口，负责统一提供评审与错误分析模板。
 */
public interface RulePromptService {

    /**
     * 读取指定模板组在当前作用域下的最终生效正文。
     *
     * @param templateCode 模板组编码
     * @param scmConfigId SCM 仓库配置 ID，可为空
     * @return 模板正文
     * @author Fantasy
     * @date 2026/05/19 16:23
     */
    String getTemplateContent(String templateCode, Long scmConfigId);

    /**
     * 构建代码评审主模板骨架。
     *
     * @param config 仓库级评审配置
     * @param scmConfigId SCM 仓库配置 ID，可为空
     * @return 渲染后的评审主模板骨架
     * @author Fantasy
     * @date 2026/05/19 07:21
     */
    String buildReviewPromptSkeleton(ReviewConfig config, Long scmConfigId);

    /**
     * 构建代码评审 JSON 修复提示词。
     *
     * @param originalResponse 原始模型返回
     * @param scmConfigId SCM 仓库配置 ID，可为空
     * @return JSON 修复提示词
     * @author Fantasy
     * @date 2026/05/19 07:21
     */
    String buildReviewJsonRepairPrompt(String originalResponse, Long scmConfigId);

    /**
     * 基于指定模板组构建通用 JSON 修复提示词。
     *
     * @param templateCode 模板组编码
     * @param originalResponse 原始模型返回
     * @param scmConfigId SCM 仓库配置 ID，可为空
     * @return JSON 修复提示词
     * @author Fantasy
     * @date 2026/05/19 16:23
     */
    String buildJsonRepairPrompt(String templateCode, String originalResponse, Long scmConfigId);

    /**
     * 解析指定语言的评审重点关注项。
     *
     * @param languageTag 语言标签
     * @param config 仓库级评审配置
     * @return 重点关注说明
     * @author Fantasy
     * @date 2026/05/17 21:29
     */
    String resolveReviewFocus(String languageTag, ReviewConfig config);

    /**
     * 获取错误分析主模板骨架。
     *
     * @param scmConfigId SCM 仓库配置 ID，可为空
     * @return 错误分析系统提示词
     * @author Fantasy
     * @date 2026/05/19 07:21
     */
    String getErrorAnalysisPromptSkeleton(Long scmConfigId);

    /**
     * 构建错误分析 JSON 修复提示词。
     *
     * @param originalResponse 原始模型返回
     * @param scmConfigId SCM 仓库配置 ID，可为空
     * @return 错误分析收尾指令
     * @author Fantasy
     * @date 2026/05/19 07:21
     */
    String buildErrorAnalysisJsonRepairPrompt(String originalResponse, Long scmConfigId);
}
