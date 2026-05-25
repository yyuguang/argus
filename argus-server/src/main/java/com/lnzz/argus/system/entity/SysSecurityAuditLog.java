package com.lnzz.argus.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统安全审计日志实体。
 * <p>审计日志只追加，不参与软删除。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_sys_security_audit_log")
public class SysSecurityAuditLog extends BaseEntity {

    /** 操作者用户ID */
    private Long actorUserId;

    /** 操作者登录账号 */
    private String actorUsername;

    /** 动作编码 */
    private String action;

    /** 资源类型 */
    private String resourceType;

    /** 资源ID */
    private String resourceId;

    /** 变更前快照 JSON */
    private String beforeJson;

    /** 变更后快照 JSON */
    private String afterJson;

    /** 结果: SUCCESS/FAILED */
    private String result;

    /** 失败原因 */
    private String failureReason;

    /** 客户端IP */
    private String clientIp;

    /** User-Agent */
    private String userAgent;

    /** 链路追踪ID */
    private String traceId;
}
