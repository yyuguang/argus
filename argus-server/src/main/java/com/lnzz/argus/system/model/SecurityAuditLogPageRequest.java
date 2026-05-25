package com.lnzz.argus.system.model;

import com.lnzz.argus.common.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 安全审计日志分页查询请求。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SecurityAuditLogPageRequest extends BasePageRequest {

    /** 操作者账号模糊查询条件。 */
    private String actorUsername;

    /** 审计动作编码。 */
    private String action;

    /** 资源类型。 */
    private String resourceType;

    /** 审计结果，例如 SUCCESS 或 FAILED。 */
    private String result;

    /** 查询开始时间，格式为 yyyy-MM-dd HH:mm:ss。 */
    private String startTime;

    /** 查询结束时间，格式为 yyyy-MM-dd HH:mm:ss。 */
    private String endTime;
}
