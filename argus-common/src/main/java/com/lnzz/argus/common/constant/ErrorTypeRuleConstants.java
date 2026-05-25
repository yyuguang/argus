package com.lnzz.argus.common.constant;

import java.util.Set;

/**
 * 错误类型识别规则常量。
 *
 * @author lnzz
 * @since 1.0.0
 */
public final class ErrorTypeRuleConstants {

    /** 规则缓存有效期，单位毫秒。 */
    public static final long RULE_CACHE_MILLIS = 30_000L;

    /** 规则加载失败后的短缓存重试间隔，单位毫秒。 */
    public static final long RULE_LOAD_RETRY_CACHE_MILLIS = 5_000L;

    /** 新建规则默认优先级。 */
    public static final int DEFAULT_PRIORITY = 100;

    /** 匹配字段：任意字段。 */
    public static final String MATCH_FIELD_ANY = "ANY";

    /** 匹配字段：异常类名。 */
    public static final String MATCH_FIELD_EXCEPTION_CLASS = "EXCEPTION_CLASS";

    /** 匹配字段：业务上报类名。 */
    public static final String MATCH_FIELD_CLASS_NAME = "CLASS_NAME";

    /** 匹配字段：异常堆栈。 */
    public static final String MATCH_FIELD_STACK_TRACE = "STACK_TRACE";

    /** 匹配字段：错误消息。 */
    public static final String MATCH_FIELD_MESSAGE = "MESSAGE";

    /** 匹配字段：HTTP 状态码。 */
    public static final String MATCH_FIELD_HTTP_STATUS = "HTTP_STATUS";

    /** 匹配模式：完全相等。 */
    public static final String MATCH_MODE_EXACT = "EXACT";

    /** 匹配模式：包含文本。 */
    public static final String MATCH_MODE_CONTAINS = "CONTAINS";

    /** 匹配模式：正则表达式。 */
    public static final String MATCH_MODE_REGEX = "REGEX";

    /** 匹配模式：数值区间。 */
    public static final String MATCH_MODE_RANGE = "RANGE";

    /** 支持的规则匹配字段集合。 */
    public static final Set<String> SUPPORTED_MATCH_FIELDS = Set.of(
            MATCH_FIELD_ANY,
            MATCH_FIELD_EXCEPTION_CLASS,
            MATCH_FIELD_CLASS_NAME,
            MATCH_FIELD_STACK_TRACE,
            MATCH_FIELD_MESSAGE,
            MATCH_FIELD_HTTP_STATUS
    );

    /** 支持的规则匹配模式集合。 */
    public static final Set<String> SUPPORTED_MATCH_MODES = Set.of(
            MATCH_MODE_EXACT,
            MATCH_MODE_CONTAINS,
            MATCH_MODE_REGEX,
            MATCH_MODE_RANGE
    );

    private ErrorTypeRuleConstants() {
    }
}
