package com.lnzz.argus.system.model;

import com.lnzz.argus.common.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageRequest extends BasePageRequest {

    /** 部门 ID，保留 id 字段以兼容现有管理端部门树筛选语义。 */
    private String id;

    /** 用户显示名称模糊查询条件。 */
    private String username;

    /** 登录账号模糊查询条件。 */
    private String account;

    /** 用户状态，1 表示启用，0 表示禁用。 */
    private Integer status;

    /** 角色 ID，用于筛选拥有指定角色的用户。 */
    private String roleId;
}
