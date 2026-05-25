package com.lnzz.argus.system.model;

/**
 * 部门创建或修改请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record DepartmentRequest(
        String parentId,
        String departmentName,
        Integer status,
        String remark,
        Integer sortOrder
) {
}
