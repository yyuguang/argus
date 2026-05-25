package com.lnzz.argus.codeindex.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.codeindex.dao.entity.AppVersionBinding;
import com.lnzz.argus.common.constant.SystemDataConstants;
import org.apache.ibatis.annotations.Mapper;

/**
 * @classname: AppVersionBindingMapper
 * @author: Fantasy
 * @date: 2026/05/19 15:20
 * @description: 应用环境源码版本绑定 Mapper，封装当前激活版本和 commit 绑定查询。
 */
@Mapper
public interface AppVersionBindingMapper extends BaseMapper<AppVersionBinding> {

    /**
     * 查询指定应用环境当前激活的源码版本绑定。
     *
     * @param appName     应用名称
     * @param environment 运行环境
     * @param scmConfigId SCM 配置 ID
     * @return 当前激活版本绑定；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/19 15:20
     */
    default AppVersionBinding selectActiveBinding(String appName, String environment, Long scmConfigId) {
        if (!hasText(appName) || !hasText(environment) || scmConfigId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<AppVersionBinding>()
                .eq(AppVersionBinding::getAppName, appName)
                .eq(AppVersionBinding::getEnvironment, environment)
                .eq(AppVersionBinding::getScmConfigId, scmConfigId)
                .eq(AppVersionBinding::getActive, true)
                .eq(AppVersionBinding::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByDesc(AppVersionBinding::getActivatedAt)
                .orderByDesc(AppVersionBinding::getId)
                .last("limit 1"));
    }

    /**
     * 查询指定应用环境和 commit 的源码版本绑定。
     *
     * @param appName     应用名称
     * @param environment 运行环境
     * @param scmConfigId SCM 配置 ID
     * @param commitSha   部署 commit SHA
     * @return 版本绑定；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/19 15:20
     */
    default AppVersionBinding selectByCommit(String appName, String environment, Long scmConfigId, String commitSha) {
        if (!hasText(appName) || !hasText(environment) || scmConfigId == null || !hasText(commitSha)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<AppVersionBinding>()
                .eq(AppVersionBinding::getAppName, appName)
                .eq(AppVersionBinding::getEnvironment, environment)
                .eq(AppVersionBinding::getScmConfigId, scmConfigId)
                .eq(AppVersionBinding::getCommitSha, commitSha)
                .eq(AppVersionBinding::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

