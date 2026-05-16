package com.lnzz.argus.config;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBizException(BizException e, HttpServletRequest request) {
        if (e.getCause() == null) {
            log.warn("业务异常: code={}, message={}, request={}",
                    e.getCode(), safeMessage(e), requestSummary(request));
        } else {
            log.warn("业务异常: code={}, message={}, request={}, rootCause={}",
                    e.getCode(), safeMessage(e), requestSummary(request), rootCauseSummary(e), e);
        }
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常 - @RequestBody
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: message={}, request={}", message, requestSummary(request));
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 参数校验异常 - @RequestParam / @PathVariable
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .map(this::formatConstraintViolation)
                .collect(Collectors.joining("; "));
        log.warn("参数约束违反: message={}, request={}", message, requestSummary(request));
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleBindException(BindException e, HttpServletRequest request) {
        String message = e.getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("绑定异常: message={}, request={}", message, requestSummary(request));
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * JSON 请求体解析失败
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
                                                     HttpServletRequest request) {
        String message = "请求体格式错误: " + safeMessage(rootCause(e));
        log.warn("请求体解析失败: message={}, request={}, rootCause={}",
                message, requestSummary(request), rootCauseSummary(e), e);
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 必填请求参数缺失
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMissingServletRequestParameter(MissingServletRequestParameterException e,
                                                            HttpServletRequest request) {
        String message = "缺少必填参数: " + e.getParameterName();
        log.warn("请求参数缺失: message={}, request={}", message, requestSummary(request), e);
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 请求参数类型转换失败
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e,
                                                        HttpServletRequest request) {
        String requiredType = e.getRequiredType() == null ? "unknown" : e.getRequiredType().getSimpleName();
        String message = "参数类型错误: " + e.getName() + " 需要 " + requiredType;
        log.warn("请求参数类型错误: message={}, value={}, request={}, rootCause={}",
                message, e.getValue(), requestSummary(request), rootCauseSummary(e), e);
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * HTTP 方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.OK)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                 HttpServletRequest request) {
        String message = "HTTP 方法不支持: " + e.getMethod();
        log.warn("HTTP 方法不支持: message={}, supported={}, request={}",
                message, e.getSupportedHttpMethods(), requestSummary(request), e);
        return Result.fail(ResultCode.PARAM_ERROR, message);
    }

    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        String errorId = UUID.randomUUID().toString().replace("-", "");
        Throwable rootCause = rootCause(e);
        log.error("系统异常: errorId={}, request={}, exceptionType={}, exceptionMessage={}, "
                        + "rootCauseType={}, rootCauseMessage={}",
                errorId,
                requestSummary(request),
                e == null ? "null" : e.getClass().getName(),
                safeMessage(e),
                rootCause == null ? "null" : rootCause.getClass().getName(),
                safeMessage(rootCause),
                e);
        return Result.fail(ResultCode.SYSTEM_ERROR, ResultCode.SYSTEM_ERROR.getMessage() + "(errorId=" + errorId + ")");
    }

    private String formatFieldError(FieldError error) {
        if (error == null) {
            return "字段校验失败";
        }
        String message = error.getDefaultMessage();
        return error.getField() + ": " + (message == null || message.isBlank() ? "字段校验失败" : message);
    }

    private String formatConstraintViolation(ConstraintViolation<?> violation) {
        if (violation == null) {
            return "参数校验失败";
        }
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }

    private String requestSummary(HttpServletRequest request) {
        if (request == null) {
            return "null";
        }
        String query = request.getQueryString();
        String uri = query == null || query.isBlank()
                ? request.getRequestURI()
                : request.getRequestURI() + "?" + query;
        return request.getMethod()
                + " " + uri
                + ", remote=" + safe(request.getRemoteAddr())
                + ", userAgent=" + safe(request.getHeader("User-Agent"))
                + ", traceId=" + firstNonBlank(
                request.getHeader("X-Trace-Id"),
                request.getHeader("traceId"),
                request.getHeader("X-B3-TraceId"),
                request.getHeader("X-Request-Id"));
    }

    private String rootCauseSummary(Throwable throwable) {
        Throwable root = rootCause(throwable);
        if (root == null) {
            return "null";
        }
        return root.getClass().getName() + ": " + safeMessage(root);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && current.getCause() != null && current.getCause() != current && depth < 32) {
            current = current.getCause();
            depth++;
        }
        return current;
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "-";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "-";
    }
}
