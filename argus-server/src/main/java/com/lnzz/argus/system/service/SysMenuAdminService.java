package com.lnzz.argus.system.service;

import com.lnzz.argus.system.entity.SysMenu;
import com.lnzz.argus.system.model.MenuOrderRequest;
import com.lnzz.argus.system.model.MenuRequest;
import com.lnzz.argus.system.model.MenuResponse;
import com.lnzz.argus.system.model.MenuStatusRequest;

import java.util.List;

/**
 * @classname: SysMenuAdminService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: 后台菜单与按钮权限管理接口。
 */
public interface SysMenuAdminService {

    /**
     * 查询菜单树和按钮权限列表。
     *
     * @return 菜单树
     */
    List<MenuResponse> tree();

    /**
     * 创建菜单并同步按钮权限。
     *
     * @param request 菜单请求
     * @return 新菜单
     */
    MenuResponse create(MenuRequest request);

    /**
     * 更新菜单并按需同步按钮权限。
     *
     * @param menuId  菜单 ID
     * @param request 菜单请求
     * @return 更新后菜单
     */
    MenuResponse update(Long menuId, MenuRequest request);

    /**
     * 批量删除菜单，存在授权引用时拒绝删除。
     *
     * @param menuIds 菜单 ID 列表
     */
    void delete(List<Long> menuIds);

    /**
     * 批量更新菜单状态。
     *
     * @param request 状态更新请求
     */
    void updateStatus(MenuStatusRequest request);

    /**
     * 批量更新菜单排序。
     *
     * @param request 排序请求
     */
    void updateOrder(MenuOrderRequest request);

    /**
     * 根据 ID 查询菜单，不存在时抛出业务异常。
     *
     * @param menuId 菜单 ID
     * @return 菜单实体
     */
    SysMenu requireMenu(Long menuId);
}
