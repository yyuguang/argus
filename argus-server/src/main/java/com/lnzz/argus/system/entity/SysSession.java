package com.lnzz.argus.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户会话实体。
 * <p>数据库只保存 Token 哈希，明文 Token 只在登录成功时返回一次。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_sys_session")
public class SysSession extends BaseEntity {

    /** 用户ID */
    private Long userId;

    /** Token 哈希 */
    private String tokenHash;

    /** 客户端IP */
    private String clientIp;

    /** User-Agent */
    private String userAgent;

    /** 签发时间 */
    private LocalDateTime issuedAt;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 是否撤销 */
    private Boolean revoked;

    /** 撤销原因 */
    private String revokedReason;

    /** 最近活跃时间 */
    private LocalDateTime lastActiveAt;
}
