package com.lnzz.argus.scm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.mapper.ScmConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * SCM 配置服务
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ScmConfigService {

    private final ScmConfigMapper scmConfigMapper;

    public List<ScmConfig> listAll() {
        return scmConfigMapper.selectList(new LambdaQueryWrapper<ScmConfig>()
                .orderByDesc(ScmConfig::getUpdateTime));
    }

    public ScmConfig getById(Long id) {
        return scmConfigMapper.selectById(id);
    }

    public ScmConfig requireById(Long id) {
        ScmConfig config = getById(id);
        if (config == null) {
            throw new BizException(ResultCode.NOT_FOUND, "SCM 配置不存在: " + id);
        }
        return config;
    }

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

    public ScmConfig saveOrUpdate(ScmConfig config) {
        normalize(config);

        if (config.getId() == null) {
            scmConfigMapper.insert(config);
            return config;
        }

        ScmConfig existing = requireById(config.getId());
        if (!StringUtils.hasText(config.getAccessToken())) {
            config.setAccessToken(existing.getAccessToken());
        }
        if (!StringUtils.hasText(config.getWebhookSecret())) {
            config.setWebhookSecret(existing.getWebhookSecret());
        }
        scmConfigMapper.updateById(config);
        return scmConfigMapper.selectById(config.getId());
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
    }

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
