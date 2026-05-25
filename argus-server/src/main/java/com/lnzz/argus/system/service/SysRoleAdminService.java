package com.lnzz.argus.system.service;

import com.lnzz.argus.system.entity.SysRole;
import com.lnzz.argus.system.model.RoleGrantRequest;
import com.lnzz.argus.system.model.RoleRequest;
import com.lnzz.argus.system.model.RoleResponse;
import com.lnzz.argus.common.result.PageResult;

import java.util.List;

/**
 * @classname: SysRoleAdminService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: 后台角色管理接口，负责角色 CRUD 与菜单按钮授权。
 */
public interface SysRoleAdminService {

    /**
     * 分页查询角色。
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @param roleName 角色名称
     * @param status   状态
     * @return 角色分页结果
     */
    PageResult<RoleResponse> page(Integer pageNo, Integer pageSize, String roleName, Integer status);

    /**
     * 创建角色并初始化授权。
     *
     * @param request 角色请求
     * @return 新角色
     */
    RoleResponse create(RoleRequest request);

    /**
     * 更新角色基础信息和授权。
     *
     * @param roleId  角色 ID
     * @param request 角色请求
     * @return 更新后角色
     */
    RoleResponse update(Long roleId, RoleRequest request);

    /**
     * 批量删除角色。
     *
     * @param roleIds 角色 ID 列表
     */
    void delete(List<Long> roleIds);

    /**
     * 覆盖角色菜单和按钮授权。
     *
     * @param roleId  角色 ID
     * @param request 授权请求
     */
    void assignMenus(Long roleId, RoleGrantRequest request);

    /**
     * 根据 ID 查询角色，不存在时抛出业务异常。
     *
     * @param roleId 角色 ID
     * @return 角色实体
     */
    SysRole requireRole(Long roleId);
}
