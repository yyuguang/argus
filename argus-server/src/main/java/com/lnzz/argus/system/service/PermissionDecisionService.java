package com.lnzz.argus.system.service;

import com.lnzz.argus.common.constant.SecurityConstants;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @classname: PermissionDecisionService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: 后台权限裁决接口，统一输出菜单、按钮和后端权限快照。
 */
public interface PermissionDecisionService {

    /**
     * 判断用户是否拥有某个后端完整权限码。
     *
     * @param userId         用户 ID
     * @param permissionCode 后端完整权限码
     * @param clientIp       客户端 IP
     * @return true 表示允许访问
     */
    boolean hasPermission(Long userId, String permissionCode, String clientIp);

    /**
     * 构建当前用户完整授权快照，供动态路由、按钮权限和登录摘要使用。
     *
     * @param userId   用户 ID
     * @param clientIp 客户端 IP
     * @param now      当前时间
     * @return 授权快照
     */
    AuthorizationSnapshot authorizationFor(Long userId, String clientIp, LocalDateTime now);

    /**
     * 用户权限快照。menuActions 保存的是前端动作值，不是完整后端权限码。
     */
    record AuthorizationSnapshot(
            Long userId,
            List<Long> roleIds,
            List<String> roleCodes,
            boolean superAdmin,
            Set<Long> menuIds,
            Map<Long, Set<String>> menuActions,
            Set<String> permissionCodes
    ) {

        /**
         * 构建空授权快照，适用于用户不存在或账号未启用的场景。
         *
         * @param userId 用户 ID
         * @return 空授权快照
         */
        public static AuthorizationSnapshot empty(Long userId) {
            return new AuthorizationSnapshot(
                    userId,
                    List.of(),
                    List.of(),
                    false,
                    new HashSet<>(),
                    new HashMap<>(),
                    new HashSet<>());
        }

        /**
         * 转换为前端登录态需要的权限摘要。
         *
         * @return 权限摘要
         */
        public List<String> permissionsSummary() {
            if (superAdmin) {
                return List.of(SecurityConstants.ALL_PERMISSION);
            }
            return new ArrayList<>(permissionCodes);
        }
    }
}
