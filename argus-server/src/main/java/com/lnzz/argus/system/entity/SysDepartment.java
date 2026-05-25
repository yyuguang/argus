package com.lnzz.argus.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统部门实体。
 * <p>用于后台用户归属和前端部门树过滤。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_sys_department")
public class SysDepartment extends BaseEntity {

    /** 父部门ID，空表示根部门 */
    private Long parentId;

    /** 部门名称 */
    private String departmentName;

    /** 部门编码，用于初始化和导入时稳定匹配 */
    private String departmentCode;

    /** 状态: ENABLED/DISABLED */
    private String status;

    /** 同级排序号 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 是否软删除 */
    private Boolean isDeleted;

    /** 乐观锁版本 */
    @Version
    private Integer version;
}
