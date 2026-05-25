package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 角色授权请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record RoleGrantRequest(List<RoleMenuGrant> menu) {
}
