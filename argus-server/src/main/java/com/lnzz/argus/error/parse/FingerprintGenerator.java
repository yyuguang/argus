package com.lnzz.argus.error.parse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 错误指纹生成器（M4-B04）
 * <p>APP_LOG 指纹公式: SHA-256(APP + appName + environment + errorType + className + methodName + lineNumber + rootCauseException + normalizedMessage)</p>
 * <p>NGINX 指纹公式: SHA-256(NGINX + appName + environment + errorType + requestUri + httpStatus + upstreamStatus + upstreamAddr)</p>
 * <p>用于同错误聚合与去重，避免每条相同异常都触发一次完整分析链路</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class FingerprintGenerator {

    /**
     * 生成错误指纹
     *
     * @param appName   应用名
     * @param errorType 错误类型
     * @param className 异常类名
     * @param methodName 方法名（可为空）
     * @param lineNumber 行号（可为空）
     * @param rootCauseClass 根因异常类名（可为空，缺省用 errorType 替代）
     * @return SHA-256 十六进制指纹
     */
    public String generate(String appName, String errorType, String className,
                           String methodName, Integer lineNumber, String rootCauseClass) {
        StringBuilder raw = new StringBuilder();
        raw.append(nullToEmpty(appName));
        raw.append('|');
        raw.append(nullToEmpty(errorType));
        raw.append('|');
        raw.append(nullToEmpty(className));
        raw.append('|');
        raw.append(methodName != null ? methodName : "");
        raw.append('|');
        raw.append(lineNumber != null ? lineNumber : "");
        raw.append('|');
        raw.append(rootCauseClass != null ? rootCauseClass : nullToEmpty(errorType));

        return sha256(raw.toString());
    }

    /**
     * 生成应用日志指纹。
     * <p>包含 environment 与归一化 message，避免不同环境混聚，同时减少动态 ID 导致的指纹漂移。</p>
     */
    public String generateApplication(String appName, String environment, String errorType,
                                      String className, String methodName, Integer lineNumber,
                                      String rootCauseClass, String message) {
        StringBuilder raw = new StringBuilder();
        raw.append("APP");
        raw.append('|').append(nullToEmpty(appName));
        raw.append('|').append(normalizeEnvironment(environment));
        raw.append('|').append(nullToEmpty(errorType));
        raw.append('|').append(nullToEmpty(className));
        raw.append('|').append(methodName != null ? methodName : "");
        raw.append('|').append(lineNumber != null ? lineNumber : "");
        raw.append('|').append(rootCauseClass != null ? rootCauseClass : nullToEmpty(errorType));
        raw.append('|').append(normalizeMessage(message));
        return sha256(raw.toString());
    }

    /**
     * 生成 Nginx 入口异常指纹。
     * <p>Nginx 没有 Java 栈帧，按入口 URI、HTTP 状态和 upstream 信息聚合。</p>
     */
    public String generateNginx(String appName, String environment, String errorType,
                                String requestUri, Integer httpStatus,
                                Integer upstreamStatus, String upstreamAddr) {
        StringBuilder raw = new StringBuilder();
        raw.append("NGINX");
        raw.append('|').append(nullToEmpty(appName));
        raw.append('|').append(normalizeEnvironment(environment));
        raw.append('|').append(nullToEmpty(errorType));
        raw.append('|').append(normalizeUri(requestUri));
        raw.append('|').append(httpStatus != null ? httpStatus : "");
        raw.append('|').append(upstreamStatus != null ? upstreamStatus : "");
        raw.append('|').append(normalizeUpstream(upstreamAddr));
        return sha256(raw.toString());
    }

    /**
     * 基于 ErrorEvent 的关键字段生成指纹
     */
    public String generate(String appName, String errorType, String className,
                           String methodName, Integer lineNumber, String rootCauseClass,
                           ParsedStackTrace parsedStack) {
        // 优先使用解析后的根因
        String rootCause = rootCauseClass;
        if ((rootCause == null || rootCause.isEmpty())
                && parsedStack != null && parsedStack.getRootCauseClass() != null) {
            rootCause = parsedStack.getRootCauseClass();
        }
        return generate(appName, errorType, className, methodName, lineNumber, rootCause);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 算法不可用", e);
            // 降级：使用 hashCode 的十六进制
            return Integer.toHexString(input.hashCode());
        }
    }

    private String nullToEmpty(String val) {
        return val != null ? val : "";
    }

    private String normalizeEnvironment(String environment) {
        return environment == null ? "" : environment.trim().toUpperCase();
    }

    private String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return message
                .replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "{uuid}")
                .replaceAll("\\b\\d{6,}\\b", "{num}")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizeUri(String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return "";
        }
        String path = requestUri.split("\\?", 2)[0];
        return path
                .replaceAll("/\\d+(?=/|$)", "/{id}")
                .replaceAll("/[0-9a-fA-F]{24,}(?=/|$)", "/{hex}")
                .toLowerCase();
    }

    private String normalizeUpstream(String upstreamAddr) {
        if (upstreamAddr == null || upstreamAddr.isBlank()) {
            return "";
        }
        return upstreamAddr.trim().toLowerCase();
    }
}
