package com.lnzz.argus.system.model;

import com.lnzz.argus.common.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色分页查询请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageRequest extends BasePageRequest {

    /** 角色名称模糊查询条件。 */
    private String roleName;

    /** 角色状态，1 表示启用，0 表示禁用。 */
    private Integer status;
}
