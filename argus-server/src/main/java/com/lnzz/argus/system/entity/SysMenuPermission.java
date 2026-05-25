package com.lnzz.argus.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单按钮权限实体。
 * <p>actionValue 给前端按钮显隐使用，permissionCode 给后端接口强校验使用。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_sys_menu_permission")
public class SysMenuPermission extends BaseEntity {

    /** 菜单ID */
    private Long menuId;

    /** 按钮权限显示名称 */
    private String label;

    /** 前端动作值 */
    private String actionValue;

    /** 后端完整权限编码 */
    private String permissionCode;

    /** 状态: ENABLED/DISABLED */
    private String status;

    /** 排序号 */
    private Integer sortOrder;

    /** 是否软删除 */
    private Boolean isDeleted;

    /** 乐观锁版本 */
    @Version
    private Integer version;
}
