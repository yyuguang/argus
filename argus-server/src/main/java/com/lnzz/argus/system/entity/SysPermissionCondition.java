package com.lnzz.argus.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 条件权限实体。
 * <p>conditionJson 保存条件参数，首版支持 TIME_RANGE 和 IP_CIDR。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_sys_permission_condition")
public class SysPermissionCondition extends BaseEntity {

    /** 条件名称 */
    private String conditionName;

    /** 条件类型: TIME_RANGE/IP_CIDR */
    private String conditionType;

    /** 条件配置 JSON */
    private String conditionJson;

    /** 状态: ENABLED/DISABLED */
    private String status;

    /** 备注 */
    private String remark;

    /** 是否软删除 */
    private Boolean isDeleted;

    /** 乐观锁版本 */
    @Version
    private Integer version;
}
