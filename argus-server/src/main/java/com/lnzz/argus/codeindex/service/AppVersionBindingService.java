package com.lnzz.argus.codeindex.service;

import com.lnzz.argus.codeindex.dto.req.AppVersionBindingReqDTO;
import com.lnzz.argus.codeindex.dto.res.AppVersionBindingResDTO;

/**
 * @classname: AppVersionBindingService
 * @author: Fantasy
 * @date: 2026/05/19 17:05
 * @description: 应用版本源码绑定服务接口，维护应用环境到 SCM commit 和源码索引的当前激活关系。
 */
public interface AppVersionBindingService {

    /**
     * 保存并激活应用版本绑定。
     *
     * @param requestDTO 应用版本绑定请求
     * @return 当前激活绑定
     */
    AppVersionBindingResDTO bind(AppVersionBindingReqDTO requestDTO);

    /**
     * 查询应用环境当前激活版本绑定。
     *
     * @param appName 应用名称
     * @param environment 环境标识
     * @param scmConfigId SCM 仓库配置 ID
     * @return 当前激活绑定
     */
    AppVersionBindingResDTO getActiveBinding(String appName, String environment, Long scmConfigId);
}
