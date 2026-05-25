package com.lnzz.argus.system.model;

import java.util.List;

/**
 * 部门响应。
 * <p>同时服务部门树、分页表格和前端编辑回显，字段需要覆盖可编辑属性，
 * 避免编辑保存时丢失排序等配置。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public record DepartmentResponse(
        String id,
        String parentId,
        String departmentName,
        Integer status,
        Integer sortOrder,
        String createTime,
        String remark,
        List<DepartmentResponse> children
) {
}
