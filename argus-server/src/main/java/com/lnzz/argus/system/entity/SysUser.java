package com.lnzz.argus.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户实体。
 * <p>只表示 Argus Portal 登录用户，不表示 SCM 提交者。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_sys_user")
public class SysUser extends BaseEntity {

    /** 所属部门ID */
    private Long departmentId;

    /** 登录账号，对应前端 account */
    private String username;

    /** 显示名称，对应前端 username */
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 密码哈希，永不返回给前端 */
    private String passwordHash;

    /** 状态: ENABLED/DISABLED/LOCKED */
    private String status;

    /** 是否需要首次修改密码 */
    private Boolean initialPasswordRequired;

    /** 连续登录失败次数 */
    private Integer failedLoginCount;

    /** 锁定截止时间 */
    private LocalDateTime lockedUntil;

    /** 最近登录时间 */
    private LocalDateTime lastLoginAt;

    /** 最近登录IP */
    private String lastLoginIp;

    /** 外部身份来源，预留 SSO/LDAP/OAuth2 */
    private String externalProvider;

    /** 外部身份ID */
    private String externalUserId;

    /** 是否软删除 */
    private Boolean isDeleted;

    /** 乐观锁版本 */
    @Version
    private Integer version;
}
