package com.lnzz.argus.system.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.system.entity.SysMenu;
import com.lnzz.argus.common.constant.SystemDataConstants;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 系统菜单 Mapper。
 * <p>封装菜单表树查询、启用过滤和唯一性查询。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 按菜单 ID 查询未软删除菜单。
     *
     * @param menuId 菜单 ID
     * @return 未删除菜单；不存在时返回 null
     */
    default SysMenu selectNonDeletedById(Long menuId) {
        if (menuId == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getId, menuId)
                .eq(SysMenu::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .last("limit 1"));
    }

    /**
     * 查询启用且未软删除菜单，并按前端路由顺序排序。
     *
     * @param enabledStatus 启用状态枚举名
     * @return 启用菜单列表
     */
    default List<SysMenu> selectEnabledOrdered(String enabledStatus) {
        return selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(SysMenu::getStatus, enabledStatus)
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
    }

    /**
     * 查询全部未软删除菜单，并按树展示顺序排序。
     *
     * @return 未删除菜单列表
     */
    default List<SysMenu> selectNonDeletedOrdered() {
        return selectList(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
    }

    /**
     * 按路由名称查询菜单，包含已软删除数据。
     *
     * @param routeName Vue 路由名称
     * @return 匹配菜单；不存在时返回 null
     */
    default SysMenu selectByRouteNameIncludeDeleted(String routeName) {
        if (!hasText(routeName)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getRouteName, routeName.trim())
                .last("limit 1"));
    }

    /**
     * 按父菜单和路由路径查询菜单，包含已软删除数据。
     *
     * @param parentId  父菜单 ID，空表示根级菜单
     * @param routePath 路由路径
     * @return 匹配菜单；不存在时返回 null
     */
    default SysMenu selectByParentAndPathIncludeDeleted(Long parentId, String routePath) {
        if (!hasText(routePath)) {
            return null;
        }
        return selectOne(new LambdaQueryWrapper<SysMenu>()
                .isNull(parentId == null, SysMenu::getParentId)
                .eq(parentId != null, SysMenu::getParentId, parentId)
                .eq(SysMenu::getRoutePath, routePath.trim())
                .last("limit 1"));
    }

    /**
     * 统计指定菜单集合中启用且未软删除的菜单数量。
     *
     * @param menuIds       菜单 ID 集合
     * @param enabledStatus 启用状态枚举名
     * @return 匹配菜单数量
     */
    default long countEnabledByIds(Collection<Long> menuIds, String enabledStatus) {
        if (menuIds == null || menuIds.isEmpty()) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .eq(SysMenu::getStatus, enabledStatus));
    }

    /**
     * 统计指定菜单的未软删除子菜单数量。
     *
     * @param parentId 父菜单 ID
     * @return 子菜单数量
     */
    default long countNonDeletedChildren(Long parentId) {
        if (parentId == null) {
            return 0;
        }
        return selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, parentId)
                .eq(SysMenu::getIsDeleted, SystemDataConstants.NOT_DELETED));
    }

    /**
     * 软删除指定菜单。
     * <p>
     * 因全局逻辑删除插件会规避 {@code updateById} 对逻辑删除列的直接赋值，菜单删除必须
     * 下沉到 Mapper 层显式写入 {@code is_deleted}、审计字段和版本号，保证管理端删除后
     * 刷新树结构不会再返回已删除菜单。
     * </p>
     *
     * @param menuId    菜单 ID
     * @param operator  当前操作者登录账号
     * @return 受影响行数，1 表示删除成功，0 表示菜单不存在或已删除
     */
    default int softDeleteById(Long menuId, String operator) {
        if (menuId == null) {
            return 0;
        }
        return update(null, new LambdaUpdateWrapper<SysMenu>()
                .eq(SysMenu::getId, menuId)
                .eq(SysMenu::getIsDeleted, SystemDataConstants.NOT_DELETED)
                .set(SysMenu::getIsDeleted, SystemDataConstants.DELETED)
                .set(SysMenu::getUpdateBy, operator)
                .set(SysMenu::getUpdateTime, LocalDateTime.now())
                .setSql("version = version + 1"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
