package com.lnzz.argus.system.model;

/**
 * 菜单绑定的按钮权限。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record MenuPermissionDto(String id, String label, String value, String permissionCode, Integer status) {
}
