package com.lnzz.argus.system.service;

import com.lnzz.argus.system.model.CurrentUserResponse;
import com.lnzz.argus.system.model.LoginRequest;
import com.lnzz.argus.system.model.LoginResponse;
import com.lnzz.argus.system.model.RouteRecord;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * @classname: AuthService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: Portal 后台认证门面接口，Controller 仅依赖该抽象层。
 */
public interface AuthService {

    /**
     * 登录并签发服务端可撤销 Token。
     *
     * @param request        登录请求
     * @param servletRequest 当前 HTTP 请求
     * @return 登录结果，包含 Token、角色和按钮权限摘要
     */
    LoginResponse login(LoginRequest request, HttpServletRequest servletRequest);

    /**
     * 注销当前登录用户，并撤销当前会话。
     */
    void logout();

    /**
     * 查询当前用户资料、部门、角色和权限摘要。
     *
     * @param clientIp 客户端 IP，用于条件权限裁决
     * @return 当前用户信息
     */
    CurrentUserResponse currentUser(String clientIp);

    /**
     * 查询当前用户可访问的 Vue Admin 动态路由。
     *
     * @param clientIp 客户端 IP，用于条件权限裁决
     * @return 动态路由树
     */
    List<RouteRecord> routes(String clientIp);
}
