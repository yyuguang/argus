package com.lnzz.argus.system.service;

import com.lnzz.argus.system.entity.SysDepartment;
import com.lnzz.argus.system.model.DepartmentRequest;
import com.lnzz.argus.system.model.DepartmentResponse;
import com.lnzz.argus.common.result.PageResult;

import java.util.List;

/**
 * @classname: SysDepartmentAdminService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: 部门管理接口，面向后台部门树、分页和写操作。
 */
public interface SysDepartmentAdminService {

    /**
     * 查询部门树。
     *
     * @return 部门树
     */
    List<DepartmentResponse> tree();

    /**
     * 分页查询部门列表。
     *
     * @param pageNo         页码
     * @param pageSize       每页大小
     * @param departmentName 部门名称
     * @param status         状态
     * @return 分页结果
     */
    PageResult<DepartmentResponse> page(Integer pageNo, Integer pageSize, String departmentName, Integer status);

    /**
     * 创建部门。
     *
     * @param request 部门请求
     * @return 新部门
     */
    DepartmentResponse create(DepartmentRequest request);

    /**
     * 更新部门。
     *
     * @param departmentId 部门 ID
     * @param request      部门请求
     * @return 更新后部门
     */
    DepartmentResponse update(Long departmentId, DepartmentRequest request);

    /**
     * 删除部门。
     *
     * @param ids 部门 ID 列表
     */
    void delete(List<Long> ids);

    /**
     * 根据 ID 查询部门，不存在时抛出业务异常。
     *
     * @param id 部门 ID
     * @return 部门实体
     */
    SysDepartment requireDepartment(Long id);
}
