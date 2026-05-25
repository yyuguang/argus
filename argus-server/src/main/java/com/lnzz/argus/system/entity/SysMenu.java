package com.lnzz.argus.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统菜单实体。
 * <p>字段直接服务 vue-element-plus-admin 动态路由。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_sys_menu")
public class SysMenu extends BaseEntity {

    /** 父菜单ID */
    private Long parentId;

    /** 菜单类型: DIRECTORY/MENU */
    private String menuType;

    /** 路由 path */
    private String routePath;

    /** 路由 name */
    private String routeName;

    /** 组件路径: #/##/views/... */
    private String componentPath;

    /** 重定向路径 */
    private String redirectPath;

    /** 菜单标题 */
    private String title;

    /** 菜单图标 */
    private String icon;

    /** 高亮菜单 */
    private String activeMenu;

    /** 是否隐藏 */
    private Boolean hidden;

    /** 是否总是显示根菜单 */
    private Boolean alwaysShow;

    /** 是否不缓存 */
    private Boolean noCache;

    /** 是否显示面包屑 */
    private Boolean breadcrumb;

    /** 是否固定标签页 */
    private Boolean affix;

    /** 是否不显示标签页 */
    private Boolean noTagsView;

    /** 隐藏时是否仍可跳转 */
    private Boolean canTo;

    /** 状态: ENABLED/DISABLED */
    private String status;

    /** 同级排序号 */
    private Integer sortOrder;

    /** 是否软删除 */
    private Boolean isDeleted;

    /** 乐观锁版本 */
    @Version
    private Integer version;
}
