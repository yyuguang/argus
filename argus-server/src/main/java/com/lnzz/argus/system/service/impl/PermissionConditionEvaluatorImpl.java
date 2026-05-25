package com.lnzz.argus.system.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.enums.PermissionConditionType;
import com.lnzz.argus.common.enums.SysStatus;
import com.lnzz.argus.system.entity.SysPermissionCondition;
import com.lnzz.argus.system.mapper.SysPermissionConditionMapper;
import com.lnzz.argus.system.service.PermissionConditionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 条件权限裁决器。
 * <p>
 * 条件权限属于中优先级能力，首版支持 TIME_RANGE 和 IP_CIDR。
 * 配置异常时返回 false，避免错误配置意外放大权限。
 * </p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionConditionEvaluatorImpl implements PermissionConditionEvaluator {

    private final SysPermissionConditionMapper conditionMapper;

    /**
     * 判断条件是否满足。conditionId 为空表示无条件，直接满足。
     *
     * @param conditionId 条件 ID
     * @param clientIp    客户端 IP
     * @param now         当前时间
     * @return true 表示条件满足
     */
    @Override
    public boolean matches(Long conditionId, String clientIp, LocalDateTime now) {
        if (conditionId == null) {
            return true;
        }
        SysPermissionCondition condition = conditionMapper.selectNonDeletedById(conditionId);
        if (condition == null || !SysStatus.enabled(condition.getStatus())) {
            log.debug("条件权限未命中: conditionId={}, reason=条件不存在、已删除或未启用", conditionId);
            return false;
        }
        try {
            Map<String, Object> payload = JSON.parseObject(condition.getConditionJson());
            if (PermissionConditionType.TIME_RANGE.name().equalsIgnoreCase(condition.getConditionType())) {
                return matchesTimeRange(payload, now == null ? LocalDateTime.now() : now);
            }
            if (PermissionConditionType.IP_CIDR.name().equalsIgnoreCase(condition.getConditionType())) {
                return matchesIpCidr(payload, clientIp);
            }
            log.warn("条件权限类型不支持: conditionId={}, conditionType={}",
                    conditionId, condition.getConditionType());
            return false;
        } catch (Exception ex) {
            log.warn("条件权限解析失败: conditionId={}, conditionType={}, clientIp={}, reason={}",
                    conditionId, condition.getConditionType(), clientIp, ex.getMessage());
            return false;
        }
    }

    private boolean matchesTimeRange(Map<String, Object> payload, LocalDateTime now) {
        String start = stringValue(payload.get("startTime"));
        String end = stringValue(payload.get("endTime"));
        if (!StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return false;
        }
        LocalTime current = now.toLocalTime();
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        if (startTime.equals(endTime)) {
            return true;
        }
        if (startTime.isBefore(endTime)) {
            return !current.isBefore(startTime) && !current.isAfter(endTime);
        }
        return !current.isBefore(startTime) || !current.isAfter(endTime);
    }

    @SuppressWarnings("unchecked")
    private boolean matchesIpCidr(Map<String, Object> payload, String clientIp) throws Exception {
        if (!StringUtils.hasText(clientIp)) {
            return false;
        }
        Object cidrsValue = payload.get("cidrs");
        List<String> cidrs = cidrsValue instanceof List<?> list
                ? (List<String>) list
                : List.of(stringValue(payload.get("cidr")));
        for (String cidr : cidrs) {
            if (StringUtils.hasText(cidr) && ipMatches(clientIp, cidr.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean ipMatches(String clientIp, String cidr) throws Exception {
        if (!cidr.contains("/")) {
            return clientIp.equals(cidr);
        }
        String[] parts = cidr.split("/");
        byte[] address = InetAddress.getByName(clientIp).getAddress();
        byte[] network = InetAddress.getByName(parts[0]).getAddress();
        int prefix = Integer.parseInt(parts[1]);
        if (address.length != network.length) {
            return false;
        }
        int fullBytes = prefix / 8;
        int remainingBits = prefix % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (address[i] != network[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = (-1) << (8 - remainingBits);
        return (address[fullBytes] & mask) == (network[fullBytes] & mask);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
