package com.lnzz.argus.system.service;

import com.lnzz.argus.system.model.RouteRecord;

import java.util.List;

/**
 * @classname: DynamicRouteService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: Vue Element Plus Admin 动态路由构造接口。
 */
public interface DynamicRouteService {

    /**
     * 为当前用户构建动态路由。
     *
     * @param userId   用户 ID
     * @param clientIp 客户端 IP
     * @return Vue Admin 动态路由树
     */
    List<RouteRecord> routesForUser(Long userId, String clientIp);
}
