package com.lnzz.argus.system.service;

import com.lnzz.argus.common.enums.SecurityAuditResourceType;

/**
 * @classname: SecurityAuditService
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: 安全审计写入接口，统一记录认证、授权和后台管理变更。
 */
public interface SecurityAuditService {

    /**
     * 记录成功审计事件。
     *
     * @param action       操作编码
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param before       变更前快照
     * @param after        变更后快照
     */
    void success(String action, SecurityAuditResourceType resourceType, Object resourceId, Object before, Object after);

    /**
     * 记录失败审计事件。
     *
     * @param action       操作编码
     * @param resourceType 资源类型
     * @param resourceId   资源 ID
     * @param reason       失败原因
     * @param before       变更前快照
     * @param after        变更后快照
     */
    void failed(String action, SecurityAuditResourceType resourceType, Object resourceId,
                String reason, Object before, Object after);
}
