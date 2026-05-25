package com.lnzz.argus.system.model;

import java.util.List;
import java.util.Map;

/**
 * 条件权限 JSON 标准载荷。
 *
 * @author lnzz
 * @since 1.0.0
 */
public record ConditionPayload(String startTime, String endTime, List<String> cidrs, Map<String, Object> extra) {
}
