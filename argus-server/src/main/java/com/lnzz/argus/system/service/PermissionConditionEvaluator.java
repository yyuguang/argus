package com.lnzz.argus.system.service;

import java.time.LocalDateTime;

/**
 * @classname: PermissionConditionEvaluator
 * @author: Fantasy
 * @date: 2026/05/16 19:00
 * @description: 条件权限裁决接口，负责时间段、IP 等附加条件判断。
 */
public interface PermissionConditionEvaluator {

    /**
     * 判断条件是否满足。conditionId 为空表示无条件，直接满足。
     *
     * @param conditionId 条件 ID
     * @param clientIp    客户端 IP
     * @param now         当前时间
     * @return true 表示条件满足
     */
    boolean matches(Long conditionId, String clientIp, LocalDateTime now);
}
