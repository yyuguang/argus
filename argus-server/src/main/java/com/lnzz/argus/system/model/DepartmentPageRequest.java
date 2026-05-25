package com.lnzz.argus.system.model;

import com.lnzz.argus.common.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门分页查询请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepartmentPageRequest extends BasePageRequest {

    /** 部门名称模糊查询条件。 */
    private String departmentName;

    /** 部门状态，1 表示启用，0 表示禁用。 */
    private Integer status;
}
