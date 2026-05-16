package com.lnzz.argus.error.parse;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.enums.ErrorType;
import com.lnzz.argus.error.entity.ErrorTypeRule;
import com.lnzz.argus.error.mapper.ErrorTypeRuleMapper;
import com.lnzz.argus.error.model.ErrorLogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 错误类型识别器（M4-B01）
 * <p>优先使用数据库配置规则；规则表为空时，回退到内置兼容规则。</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class ErrorTypeIdentifier {

    private static final long RULE_CACHE_MILLIS = 30_000L;
    private static final Pattern EXCEPTION_TOKEN = Pattern.compile(
            "([\\w.$]+(?:Exception|Error|Throwable))");
    private static final Pattern EXCEPTION_TYPE_META = Pattern.compile(
            "(?:exceptionType|rootCauseType)=([\\w.$]+(?:Exception|Error|Throwable))");

    @Autowired(required = false)
    private ErrorTypeRuleMapper ruleMapper;

    private volatile List<ErrorTypeRule> cachedRules = List.of();
    private volatile long cacheExpireAt;

    /**
     * 根据日志条目识别错误类型
     */
    public ErrorType identify(ErrorLogEntry entry) {
        List<ErrorTypeRule> rules = enabledRules();
        if (!rules.isEmpty()) {
            ErrorType configured = identifyByRules(entry, rules);
            if (configured != null) {
                return configured;
            }
            log.debug("错误类型配置规则未命中，返回 UNKNOWN");
            return ErrorType.UNKNOWN;
        }

        String className = entry.getClassName();
        String stackTrace = entry.getStackTrace();
        String message = entry.getMessage();

        // 1. className 精确匹配（最高优先级）
        ErrorType result = ErrorType.fromClassName(className);
        if (result != null) {
            log.debug("错误类型由 className 匹配: className={}, type={}", className, result);
            return result;
        }

        // 2. 异常栈链匹配
        if (stackTrace != null && !stackTrace.isEmpty()) {
            result = ErrorType.fromStackTrace(stackTrace);
            if (result != null) {
                log.debug("错误类型由 stackTrace 匹配: type={}", result);
                return result;
            }
        }

        // 3. 消息关键词匹配
        if (message != null && !message.isEmpty()) {
            result = ErrorType.fromMessage(message);
            if (result != null) {
                log.debug("错误类型由 message 关键词匹配: type={}", result);
                return result;
            }
        }

        log.debug("无法识别错误类型，返回 UNKNOWN: className={}", className);
        return ErrorType.UNKNOWN;
    }

    /**
     * M4-B07: 根据 Nginx HTTP 状态码识别入口异常类型
     *
     * @param httpStatus Nginx access log 中的 HTTP 状态码
     * @return 对应的 Nginx 错误类型，无法识别时返回 null
     */
    public ErrorType identifyNginxError(Integer httpStatus) {
        if (httpStatus == null) {
            return null;
        }
        List<ErrorTypeRule> rules = enabledRules().stream()
                .filter(rule -> "HTTP_STATUS".equalsIgnoreCase(rule.getMatchField()))
                .toList();
        if (!rules.isEmpty()) {
            for (ErrorTypeRule rule : rules) {
                if (matchesHttpStatus(rule, httpStatus)) {
                    ErrorType type = toErrorType(rule.getErrorType());
                    if (type != null) {
                        log.debug("Nginx 错误类型由配置规则匹配: status={}, type={}", httpStatus, type);
                        return type;
                    }
                }
            }
            return null;
        }
        if (httpStatus == 502) {
            return ErrorType.NGINX_502;
        }
        if (httpStatus == 503) {
            return ErrorType.NGINX_503;
        }
        if (httpStatus == 504) {
            return ErrorType.NGINX_504;
        }
        if (httpStatus == 499) {
            return ErrorType.NGINX_499;
        }
        if (httpStatus >= 500) {
            return ErrorType.NGINX_5XX;
        }
        if (httpStatus >= 400) {
            return ErrorType.NGINX_4XX;
        }
        return null;
    }

    /**
     * 根据已入库的事件字段识别错误类型（用于补解析场景）
     */
    public ErrorType identify(String className, String stackTrace, String message) {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setClassName(className);
        entry.setStackTrace(stackTrace);
        entry.setMessage(message);
        List<ErrorTypeRule> rules = enabledRules();
        if (!rules.isEmpty()) {
            ErrorType configured = identifyByRules(entry, rules);
            return configured != null ? configured : ErrorType.UNKNOWN;
        }

        // 1. className
        ErrorType result = ErrorType.fromClassName(className);
        if (result != null) return result;

        // 2. stackTrace
        if (stackTrace != null && !stackTrace.isEmpty()) {
            result = ErrorType.fromStackTrace(stackTrace);
            if (result != null) return result;
        }

        // 3. message
        if (message != null && !message.isEmpty()) {
            result = ErrorType.fromMessage(message);
            if (result != null) return result;
        }

        return ErrorType.UNKNOWN;
    }

    public void invalidateRuleCache() {
        cacheExpireAt = 0L;
        cachedRules = List.of();
    }

    void setRuleMapper(ErrorTypeRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
        invalidateRuleCache();
    }

    private ErrorType identifyByRules(ErrorLogEntry entry, List<ErrorTypeRule> rules) {
        for (ErrorTypeRule rule : rules) {
            if ("HTTP_STATUS".equalsIgnoreCase(rule.getMatchField())) {
                continue;
            }
            if (matches(rule, entry)) {
                ErrorType type = toErrorType(rule.getErrorType());
                if (type != null) {
                    log.debug("错误类型由配置规则匹配: ruleId={}, ruleName={}, type={}",
                            rule.getId(), rule.getRuleName(), type);
                    return type;
                }
            }
        }
        return null;
    }

    private List<ErrorTypeRule> enabledRules() {
        if (ruleMapper == null) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        if (now < cacheExpireAt) {
            return cachedRules;
        }
        try {
            List<ErrorTypeRule> rules = ruleMapper.selectList(new LambdaQueryWrapper<ErrorTypeRule>()
                    .eq(ErrorTypeRule::getEnabled, true)
                    .orderByAsc(ErrorTypeRule::getPriority)
                    .orderByAsc(ErrorTypeRule::getId));
            cachedRules = rules == null ? List.of() : rules;
            cacheExpireAt = now + RULE_CACHE_MILLIS;
            return cachedRules;
        } catch (Exception e) {
            log.warn("加载错误类型识别规则失败，临时回退内置规则: {}", e.getMessage());
            cachedRules = List.of();
            cacheExpireAt = now + 5_000L;
            return cachedRules;
        }
    }

    private boolean matches(ErrorTypeRule rule, ErrorLogEntry entry) {
        String field = safeUpper(rule.getMatchField());
        String mode = safeUpper(rule.getMatchMode());
        String pattern = rule.getPattern();
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        if ("ANY".equals(field)) {
            return matchesText(mode, pattern, entry.getClassName())
                    || matchesText(mode, pattern, entry.getMessage())
                    || matchesText(mode, pattern, entry.getStackTrace())
                    || exceptionClasses(entry).stream().anyMatch(value -> matchesClass(mode, pattern, value));
        }
        if ("EXCEPTION_CLASS".equals(field)) {
            return exceptionClasses(entry).stream().anyMatch(value -> matchesClass(mode, pattern, value));
        }
        if ("CLASS_NAME".equals(field)) {
            return matchesClass(mode, pattern, entry.getClassName());
        }
        if ("MESSAGE".equals(field)) {
            return matchesText(mode, pattern, entry.getMessage());
        }
        if ("STACK_TRACE".equals(field)) {
            return matchesText(mode, pattern, entry.getStackTrace());
        }
        return false;
    }

    private boolean matchesHttpStatus(ErrorTypeRule rule, Integer httpStatus) {
        if (httpStatus == null || rule.getPattern() == null) {
            return false;
        }
        String mode = safeUpper(rule.getMatchMode());
        String pattern = rule.getPattern().trim();
        if ("EXACT".equals(mode)) {
            return String.valueOf(httpStatus).equals(pattern);
        }
        if ("RANGE".equals(mode)) {
            String[] parts = pattern.split("-", 2);
            if (parts.length != 2) {
                return false;
            }
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                return httpStatus >= start && httpStatus <= end;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean matchesText(String mode, String pattern, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if ("EXACT".equals(mode)) {
            return value.trim().equalsIgnoreCase(pattern.trim());
        }
        if ("REGEX".equals(mode)) {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                    .matcher(value)
                    .find();
        }
        return value.toLowerCase(Locale.ROOT).contains(pattern.toLowerCase(Locale.ROOT));
    }

    private boolean matchesClass(String mode, String pattern, String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        if ("EXACT".equals(mode)) {
            return className.equals(pattern) || shortName(className).equals(pattern);
        }
        return matchesText(mode, pattern, className);
    }

    private List<String> exceptionClasses(ErrorLogEntry entry) {
        List<String> result = new ArrayList<>();
        addIfNotBlank(result, entry.getClassName());
        collectExceptionTypeMetadata(entry.getMessage(), result);
        collectExceptionTypeMetadata(entry.getStackTrace(), result);
        collectExceptionTokens(entry.getStackTrace(), result);
        collectExceptionTokens(entry.getMessage(), result);
        return result.stream().distinct().toList();
    }

    private void collectExceptionTypeMetadata(String text, List<String> result) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = EXCEPTION_TYPE_META.matcher(text);
        while (matcher.find()) {
            addIfNotBlank(result, matcher.group(1));
        }
    }

    private void collectExceptionTokens(String text, List<String> result) {
        if (text == null || text.isBlank()) {
            return;
        }
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("at ")) {
                continue;
            }
            Matcher matcher = EXCEPTION_TOKEN.matcher(trimmed);
            if (matcher.find()) {
                addIfNotBlank(result, matcher.group(1));
            }
        }
    }

    private void addIfNotBlank(List<String> result, String value) {
        if (value != null && !value.isBlank()) {
            result.add(value.trim());
        }
    }

    private ErrorType toErrorType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ErrorType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("错误类型规则配置了不支持的 errorType: {}", value);
            return null;
        }
    }

    private String shortName(String className) {
        int index = className.lastIndexOf('.');
        return index >= 0 ? className.substring(index + 1) : className;
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
