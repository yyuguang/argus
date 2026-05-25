package com.lnzz.argus.scm.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.scm.entity.ScmConfig;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

/**
 * SCM 配置 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ScmConfigMapper extends BaseMapper<ScmConfig> {

    /**
     * 分页查询 SCM 配置。
     *
     * @param page        分页对象
     * @param scmProvider SCM 提供方
     * @param enabled     启用状态
     * @param keyword     关键字
     * @return SCM 配置分页结果
     */
    default Page<ScmConfig> selectPageByCondition(Page<ScmConfig> page,
                                                  String scmProvider,
                                                  Boolean enabled,
                                                  String keyword) {
        String provider = normalizeProvider(scmProvider);
        String searchKeyword = trimToNull(keyword);
        LambdaQueryWrapper<ScmConfig> wrapper = new LambdaQueryWrapper<ScmConfig>()
                .eq(StringUtils.hasText(provider), ScmConfig::getScmProvider, provider)
                .eq(enabled != null, ScmConfig::getEnabled, enabled)
                .and(StringUtils.hasText(searchKeyword), condition -> condition
                        .like(ScmConfig::getProjectName, searchKeyword)
                        .or()
                        .like(ScmConfig::getRepoOwner, searchKeyword)
                        .or()
                        .like(ScmConfig::getRepoName, searchKeyword)
                        .or()
                        .like(ScmConfig::getApiBaseUrl, searchKeyword)
                        .or()
                        .like(ScmConfig::getDescription, searchKeyword))
                .orderByDesc(ScmConfig::getUpdateTime)
                .orderByDesc(ScmConfig::getId);
        return selectPage(page, wrapper);
    }

    private String normalizeProvider(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase() : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
