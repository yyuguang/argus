package com.lnzz.argus.error.parse;

import com.lnzz.argus.common.enums.ErrorType;
import com.lnzz.argus.error.model.ErrorLogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 错误类型识别器（M4-B01）
 * <p>按优先级：className 精确匹配 > 异常栈链匹配 > 消息关键词匹配 > UNKNOWN</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
public class ErrorTypeIdentifier {

    /**
     * 根据日志条目识别错误类型
     */
    public ErrorType identify(ErrorLogEntry entry) {
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
}
