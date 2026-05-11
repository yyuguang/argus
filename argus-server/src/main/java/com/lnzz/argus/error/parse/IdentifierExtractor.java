package com.lnzz.argus.error.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关键标识提取器（M4-B03）
 * <p>当 Agent 未显式提供 traceId/businessKey/interfaceRef 时，从 message/上下文/类名中提取</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class IdentifierExtractor {

    // traceId 常见格式: traceId=xxx / traceId:xxx / [traceId xxx] / trace_id=xxx
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile(
            "(?:traceId|trace_id|trace-id|TraceId|TRACE_ID|X-B3-TraceId)\\s*[=:]\\s*([a-zA-Z0-9_-]{8,64})");

    // UUID 格式: 8-4-4-4-12（traceId 常见格式）
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12})\\b");

    // 16-32 位十六进制 traceId
    private static final Pattern HEX_TRACE_ID = Pattern.compile(
            "(?:^|\\s|=|:)([a-fA-F0-9]{16,32})(?:\\s|$|,|\\])");

    // businessKey 常见模式: orderId/order_id/userId/taskId 等
    private static final Pattern BIZ_KEY_PATTERN = Pattern.compile(
            "(?:orderId|order_id|ORDER_ID|userId|user_id|USER_ID|taskId|task_id|TASK_ID"
                    + "|bizId|biz_id|BIZ_ID|businessKey|business_key"
                    + "|merchantId|merchant_id|shopId|shop_id|warehouseId|warehouse_id"
                    + "|skuId|sku_id|spuId|spu_id|requestId|request_id)"
                    + "\\s*[=:]\\s*([a-zA-Z0-9_-]{1,64})");

    /**
     * 从消息文本中提取 traceId
     */
    public String extractTraceId(String message, String existingTraceId) {
        if (existingTraceId != null && !existingTraceId.isEmpty()) {
            return existingTraceId;
        }
        if (message == null || message.isEmpty()) {
            return null;
        }

        // 1. 匹配 traceId=xxx 模式
        Matcher m = TRACE_ID_PATTERN.matcher(message);
        if (m.find()) {
            String found = m.group(1);
            log.debug("从消息中提取 traceId: {} (模式匹配)", found);
            return found;
        }

        // 2. 匹配 UUID 格式（常见于分布式链路追踪）
        m = UUID_PATTERN.matcher(message);
        if (m.find()) {
            String found = m.group(1);
            log.debug("从消息中提取 traceId: {} (UUID匹配)", found);
            return found;
        }

        // 3. 匹配长十六进制串
        m = HEX_TRACE_ID.matcher(message);
        if (m.find()) {
            String found = m.group(1);
            log.debug("从消息中提取 traceId: {} (十六进制匹配)", found);
            return found;
        }

        return null;
    }

    /**
     * 从消息文本中提取 businessKey
     */
    public String extractBusinessKey(String message, String existingBizKey) {
        if (existingBizKey != null && !existingBizKey.isEmpty()) {
            return existingBizKey;
        }
        if (message == null || message.isEmpty()) {
            return null;
        }

        Matcher m = BIZ_KEY_PATTERN.matcher(message);
        if (m.find()) {
            String found = m.group(1);
            log.debug("从消息中提取 businessKey: {}", found);
            return found;
        }
        return null;
    }

    /**
     * 推断 interfaceRef
     * <p>优先使用现有值，否则从 className.methodName 或 requestUri 生成</p>
     */
    public String inferInterfaceRef(String existingRef, String className, String methodName, String requestUri) {
        if (existingRef != null && !existingRef.isEmpty()) {
            return existingRef;
        }
        // 有 className + methodName → 拼接为接口引用
        if (className != null && methodName != null) {
            String simpleName = className.contains(".")
                    ? className.substring(className.lastIndexOf('.') + 1)
                    : className;
            return simpleName + "." + methodName;
        }
        // 有 requestUri → 作为接口引用
        if (requestUri != null && !requestUri.isEmpty()) {
            return requestUri;
        }
        return null;
    }
}
