package com.lnzz.argus.error.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 推送的错误日志条目
 * <p>统一协议模型，兼容应用日志与 Nginx 日志双源采集</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class ErrorLogEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // ======================== 必须字段 ========================

    /** 应用名称 */
    @NotBlank(message = "appName 不能为空")
    private String appName;

    /** 日志ID（Agent 生成，用于幂等去重） */
    @NotBlank(message = "logId 不能为空")
    private String logId;

    /** 日志产生时间 */
    @NotNull(message = "logTime 不能为空")
    private LocalDateTime logTime;

    /** 日志级别: ERROR/WARN/INFO */
    @NotBlank(message = "logLevel 不能为空")
    private String logLevel;

    /** 日志消息 */
    @NotBlank(message = "message 不能为空")
    private String message;

    // ======================== 推荐字段 ========================

    /** 异常栈文本 */
    private String stackTrace;

    /** 分布式追踪ID */
    private String traceId;

    /** 日志来源: APP_LOG / NGINX_ACCESS / NGINX_ERROR */
    private String logSource;

    /** 部署环境: dev/test/prod */
    private String environment;

    /** 主机名/IP */
    private String host;

    // ======================== 增强字段 ========================

    /** 业务主键（订单号/用户ID等） */
    private String businessKey;

    /** 关联接口标识 */
    private String interfaceRef;

    /** 出错类全限定名 */
    private String className;

    /** 出错方法名 */
    private String methodName;

    /** 出错行号 */
    private Integer lineNumber;

    /** 请求参数/请求体快照 */
    private Map<String, Object> requestInfo;

    /** 上下文日志（出错前N条） */
    private List<String> contextLogs;

    // ======================== Nginx 特有字段 ========================

    /** Nginx 请求URI */
    private String requestUri;

    /** Nginx HTTP 状态码 */
    private Integer httpStatus;

    /** Nginx upstream 地址 */
    private String upstreamAddr;

    /** Nginx upstream 状态 */
    private Integer upstreamStatus;

    /** Nginx 请求耗时(s) */
    private Double requestTime;

    /** Nginx 客户端IP */
    private String remoteAddr;

    /** Nginx 请求方法 */
    private String requestMethod;

    /** Nginx upstream 连接时间(s) */
    private Double upstreamConnectTime;

    /** Nginx upstream 响应时间(s) */
    private Double upstreamResponseTime;
}
