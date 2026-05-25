package com.lnzz.argus.scm.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.constant.NotificationConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.mapper.ScmConfigMapper;
import com.lnzz.argus.scm.service.ScmReviewConfigSupport;
import com.lnzz.argus.scm.service.ScmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * SCM 配置服务实现
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScmConfigServiceImpl implements ScmConfigService {

    private final ScmConfigMapper scmConfigMapper;
    private final ScmReviewConfigSupport scmReviewConfigSupport;

    @Override
    public Page<ScmConfig> pageConfigs(int pageNo,
                                       int pageSize,
                                       String scmProvider,
                                       Boolean enabled,
                                       String keyword) {
        log.debug("分页查询 SCM 配置: pageNo={}, pageSize={}, provider={}, enabled={}, keyword={}",
                pageNo, pageSize, scmProvider, enabled, keyword);
        return scmConfigMapper.selectPageByCondition(new Page<>(pageNo, pageSize), scmProvider, enabled, keyword);
    }

    @Override
    public List<ScmConfig> listAll() {
        List<ScmConfig> configs = scmConfigMapper.selectList(new LambdaQueryWrapper<ScmConfig>()
                .orderByDesc(ScmConfig::getUpdateTime));
        log.debug("查询全部 SCM 配置完成: count={}", configs.size());
        return configs;
    }

    @Override
    public ScmConfig getById(Long id) {
        return scmConfigMapper.selectById(id);
    }

    @Override
    public ScmConfig requireById(Long id) {
        ScmConfig config = getById(id);
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND, "SCM 配置不存在: " + id);
        }
        return config;
    }

    @Override
    public ScmConfig resolveConfig(String provider, Long projectId, String repoOwner, String repoName) {
        LambdaQueryWrapper<ScmConfig> wrapper = new LambdaQueryWrapper<ScmConfig>()
                .eq(ScmConfig::getScmProvider, provider)
                .eq(ScmConfig::getEnabled, true)
                .last("limit 1");

        if (projectId != null) {
            wrapper.eq(ScmConfig::getProjectId, projectId);
        } else if (StringUtils.hasText(repoOwner) && StringUtils.hasText(repoName)) {
            wrapper.eq(ScmConfig::getRepoOwner, repoOwner)
                    .eq(ScmConfig::getRepoName, repoName);
        } else {
            return null;
        }

        return scmConfigMapper.selectOne(wrapper);
    }

    @Override
    public ScmConfig saveOrUpdate(ScmConfig config) {
        log.info("开始保存 SCM 配置: configId={}, provider={}, projectId={}, repo={}/{}",
                config.getId(), config.getScmProvider(), config.getProjectId(),
                config.getRepoOwner(), config.getRepoName());
        normalize(config);

        if (config.getId() == null) {
            scmConfigMapper.insert(config);
            log.info("创建 SCM 配置: configId={}, provider={}, projectId={}, repo={}/{}",
                    config.getId(), config.getScmProvider(), config.getProjectId(),
                    config.getRepoOwner(), config.getRepoName());
            return config;
        }

        ScmConfig existing = requireById(config.getId());
        if (!StringUtils.hasText(config.getAccessToken())) {
            config.setAccessToken(existing.getAccessToken());
        }
        if (!StringUtils.hasText(config.getWebhookSecret())) {
            config.setWebhookSecret(existing.getWebhookSecret());
        }
        if (config.getWechatNotifyWebhook() == null) {
            config.setWechatNotifyWebhook(existing.getWechatNotifyWebhook());
        }
        if (config.getFeishuNotifyWebhook() == null) {
            config.setFeishuNotifyWebhook(existing.getFeishuNotifyWebhook());
        }
        if (config.getDingtalkNotifyWebhook() == null) {
            config.setDingtalkNotifyWebhook(existing.getDingtalkNotifyWebhook());
        }
        config.setReviewConfig(scmReviewConfigSupport.mergeReviewConfigForPersist(
                config.getReviewConfig(),
                existing.getReviewConfig(),
                config.getWechatNotifyEnabled() != null ? config.getWechatNotifyEnabled() : existing.getWechatNotifyEnabled(),
                StringUtils.hasText(config.getWechatNotifyWebhook()) ? config.getWechatNotifyWebhook() : existing.getWechatNotifyWebhook(),
                config.getFeishuNotifyEnabled() != null ? config.getFeishuNotifyEnabled() : existing.getFeishuNotifyEnabled(),
                StringUtils.hasText(config.getFeishuNotifyWebhook()) ? config.getFeishuNotifyWebhook() : existing.getFeishuNotifyWebhook(),
                config.getDingtalkNotifyEnabled() != null ? config.getDingtalkNotifyEnabled() : existing.getDingtalkNotifyEnabled(),
                StringUtils.hasText(config.getDingtalkNotifyWebhook()) ? config.getDingtalkNotifyWebhook() : existing.getDingtalkNotifyWebhook()));
        log.debug("SCM 配置更新前完成 reviewConfig 合并: configId={}", config.getId());
        syncPlatformFields(config);
        scmConfigMapper.updateById(config);
        ScmConfig updated = scmConfigMapper.selectById(config.getId());
        log.info("更新 SCM 配置: configId={}, provider={}, projectId={}, enabled={}",
                config.getId(), config.getScmProvider(), config.getProjectId(), config.getEnabled());
        return updated;
    }

    private void normalize(ScmConfig config) {
        if (config.getScmProvider() != null) {
            config.setScmProvider(config.getScmProvider().trim().toLowerCase());
        }
        if (config.getEnabled() == null) {
            config.setEnabled(Boolean.TRUE);
        }
        if (config.getRepoOwner() != null) {
            config.setRepoOwner(config.getRepoOwner().trim());
        }
        if (config.getRepoName() != null) {
            config.setRepoName(config.getRepoName().trim());
        }
        if (config.getMaxRelatedClasses() == null || config.getMaxRelatedClasses() <= 0) {
            config.setMaxRelatedClasses(5);
        }
        if (config.getMaxContextTokens() == null || config.getMaxContextTokens() <= 0) {
            config.setMaxContextTokens(16000);
        }
        if (config.getReviewParallelism() == null || config.getReviewParallelism() <= 0) {
            config.setReviewParallelism(3);
        }
        config.setReviewConfig(scmReviewConfigSupport.mergeReviewConfigForPersist(
                config.getReviewConfig(),
                null,
                config.getWechatNotifyEnabled(),
                config.getWechatNotifyWebhook(),
                config.getFeishuNotifyEnabled(),
                config.getFeishuNotifyWebhook(),
                config.getDingtalkNotifyEnabled(),
                config.getDingtalkNotifyWebhook()));
        log.debug("SCM 配置基础字段标准化完成: provider={}, enabled={}, maxRelatedClasses={}, maxContextTokens={}, reviewParallelism={}",
                config.getScmProvider(), config.getEnabled(), config.getMaxRelatedClasses(),
                config.getMaxContextTokens(), config.getReviewParallelism());
        syncPlatformFields(config);
    }

    private void syncPlatformFields(ScmConfig config) {
        // 独立列用于列表查询和兼容旧链路，真实配置统一落到 reviewConfig.notification.platforms。
        ReviewConfig reviewConfig = scmReviewConfigSupport.resolveReviewConfig(
                config.getReviewConfig(),
                config.getWechatNotifyEnabled(),
                config.getWechatNotifyWebhook(),
                config.getFeishuNotifyEnabled(),
                config.getFeishuNotifyWebhook(),
                config.getDingtalkNotifyEnabled(),
                config.getDingtalkNotifyWebhook());
        config.setReviewConfig(JSON.toJSONString(reviewConfig));
        config.setWechatNotifyEnabled(scmReviewConfigSupport.resolveLegacyWechatEnabled(reviewConfig));
        config.setWechatNotifyWebhook(scmReviewConfigSupport.resolveLegacyWechatWebhook(reviewConfig));
        config.setFeishuNotifyEnabled(scmReviewConfigSupport.resolvePlatformEnabled(
                reviewConfig, NotificationConstants.PLATFORM_FEISHU));
        config.setFeishuNotifyWebhook(scmReviewConfigSupport.resolvePlatformWebhook(
                reviewConfig, NotificationConstants.PLATFORM_FEISHU));
        config.setDingtalkNotifyEnabled(scmReviewConfigSupport.resolvePlatformEnabled(
                reviewConfig, NotificationConstants.PLATFORM_DINGTALK));
        config.setDingtalkNotifyWebhook(scmReviewConfigSupport.resolvePlatformWebhook(
                reviewConfig, NotificationConstants.PLATFORM_DINGTALK));
        log.debug("SCM 配置通知平台字段同步完成: wechat={}, feishu={}, dingtalk={}",
                config.getWechatNotifyEnabled(), config.getFeishuNotifyEnabled(), config.getDingtalkNotifyEnabled());
    }

    @Override
    public String maskSecret(String secret) {
        if (!StringUtils.hasText(secret)) {
            return "";
        }
        if (secret.length() <= 8) {
            return "********";
        }
        return secret.substring(0, 4) + "********" + secret.substring(secret.length() - 4);
    }
}
