package com.lnzz.argus.common.enums;

/**
 * 用户级权限覆盖效果。
 * <p>DENY 的优先级高于 ALLOW 和角色授权。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum PermissionEffect {

    /** 临时授予用户某个页面或按钮权限。 */
    ALLOW,

    /** 临时拒绝用户从角色继承到的某个页面或按钮权限。 */
    DENY
}
