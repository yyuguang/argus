package com.lnzz.argus.error.parse;

import java.util.Arrays;
import java.util.Set;

/**
 * 错误类型枚举 —— 12 种标准错误分类
 *
 * @author lnzz
 * @since 1.0.0
 */
public enum ErrorType {

    NULL_POINTER("空指针异常",
            Set.of("NullPointerException", "java.lang.NullPointerException"),
            Set.of("NullPointerException"),
            Set.of("null", "NullPointerException", "Cannot invoke.*because.*is null")),

    CLASS_CAST("类型转换异常",
            Set.of("ClassCastException", "java.lang.ClassCastException"),
            Set.of("ClassCastException"),
            Set.of("cannot be cast", "ClassCastException")),

    INDEX_OUT_OF_BOUNDS("索引越界异常",
            Set.of("IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException",
                   "StringIndexOutOfBoundsException", "java.lang.IndexOutOfBoundsException",
                   "java.lang.ArrayIndexOutOfBoundsException"),
            Set.of("IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException"),
            Set.of("Index.*out of bounds", "index out of range")),

    IO_EXCEPTION("IO异常",
            Set.of("IOException", "java.io.IOException", "FileNotFoundException",
                   "java.io.FileNotFoundException", "EOFException"),
            Set.of("IOException", "FileNotFoundException", "EOFException"),
            Set.of("Connection reset", "Broken pipe", "IOException")),

    TIMEOUT("超时异常",
            Set.of("TimeoutException", "ReadTimeoutException", "ConnectTimeoutException",
                   "java.util.concurrent.TimeoutException", "SocketTimeoutException",
                   "java.net.SocketTimeoutException"),
            Set.of("TimeoutException", "ReadTimeoutException", "ConnectTimeoutException",
                   "SocketTimeoutException"),
            Set.of("timeout", "timed out", "TimeoutException", "read timed out", "connect timed out")),

    CONNECTION_REFUSED("连接拒绝/不可达",
            Set.of("ConnectException", "java.net.ConnectException",
                   "NoRouteToHostException", "java.net.NoRouteToHostException"),
            Set.of("ConnectException", "NoRouteToHostException"),
            Set.of("Connection refused", "connect refused", "No route to host",
                   "ConnectException", "Network is unreachable")),

    SQL_EXCEPTION("数据库异常",
            Set.of("SQLException", "java.sql.SQLException", "DataAccessException",
                   "org.springframework.dao.DataAccessException",
                   "DataIntegrityViolationException", "DuplicateKeyException"),
            Set.of("SQLException", "DataAccessException", "DataIntegrityViolationException",
                   "DuplicateKeyException"),
            Set.of("SQLException", "DataAccessException", "DataIntegrityViolationException",
                   "ORA-", "SQLSTATE", "jdbc", "MySQL", "PostgreSQL")),

    BIZ_EXCEPTION("业务异常",
            Set.of("BizException", "BusinessException", "ServiceException",
                   "com.lnzz.argus.common.exception.BizException"),
            Set.of("BizException", "BusinessException", "ServiceException"),
            Set.of("BizException", "BusinessException")),

    HTTP_ERROR("HTTP调用异常",
            Set.of("HttpClientErrorException", "HttpServerErrorException",
                   "org.springframework.web.client.HttpClientErrorException",
                   "org.springframework.web.client.HttpServerErrorException",
                   "FeignException", "feign.FeignException"),
            Set.of("HttpClientErrorException", "HttpServerErrorException", "FeignException"),
            Set.of("FeignException", "HttpClientErrorException", "HttpServerErrorException",
                   "404 Not Found", "500 Internal Server Error", "503 Service Unavailable")),

    MQ_ERROR("消息队列异常",
            Set.of("JMSException", "AmqpException", "KafkaException",
                   "org.springframework.amqp.AmqpException",
                   "org.apache.kafka.common.errors.KafkaException"),
            Set.of("JMSException", "AmqpException", "KafkaException"),
            Set.of("AmqpException", "KafkaException", "JMSException",
                   "RabbitMQ", "Kafka", "RocketMQ", "MQ")),

    SERIALIZATION_ERROR("序列化异常",
            Set.of("JsonProcessingException", "JsonParseException", "JsonMappingException",
                   "com.fasterxml.jackson.core.JsonProcessingException",
                   "NotSerializableException", "java.io.NotSerializableException"),
            Set.of("JsonProcessingException", "JsonParseException", "JsonMappingException",
                   "NotSerializableException"),
            Set.of("JsonProcessingException", "JsonParseException", "NotSerializableException",
                   "Cannot deserialize", "Cannot serialize", "Unrecognized field")),

    NGINX_5XX("Nginx 5xx 服务端异常",
            Set.of(),
            Set.of(),
            Set.of("500", "501", "502", "503", "504", "505", "5xx")),

    NGINX_502("Nginx 502 Bad Gateway",
            Set.of(),
            Set.of(),
            Set.of("502", "Bad Gateway")),

    NGINX_503("Nginx 503 Service Unavailable",
            Set.of(),
            Set.of(),
            Set.of("503", "Service Unavailable", "Service Temporarily Unavailable")),

    NGINX_504("Nginx 504 Gateway Timeout",
            Set.of(),
            Set.of(),
            Set.of("504", "Gateway Timeout", "Gateway Time-out")),

    NGINX_499("Nginx 499 Client Closed Request",
            Set.of(),
            Set.of(),
            Set.of("499", "Client Closed Request")),

    NGINX_4XX("Nginx 4xx 客户端异常",
            Set.of(),
            Set.of(),
            Set.of("400", "401", "403", "404", "405", "408", "4xx")),

    UNKNOWN("未知异常",
            Set.of(),
            Set.of(),
            Set.of());

    private final String description;
    private final Set<String> classNameMatches;
    private final Set<String> stackTraceMatches;
    private final Set<String> messageKeywords;

    ErrorType(String description,
              Set<String> classNameMatches,
              Set<String> stackTraceMatches,
              Set<String> messageKeywords) {
        this.description = description;
        this.classNameMatches = classNameMatches;
        this.stackTraceMatches = stackTraceMatches;
        this.messageKeywords = messageKeywords;
    }

    public String getDescription() { return description; }

    /**
     * 根据异常类名精确匹配（最高优先级）
     */
    public static ErrorType fromClassName(String className) {
        if (className == null || className.isEmpty()) {
            return null;
        }
        String shortName = className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1)
                : className;
        for (ErrorType type : values()) {
            if (type.classNameMatches.contains(className)
                    || type.classNameMatches.contains(shortName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 从异常栈文本中匹配（中等优先级）
     */
    public static ErrorType fromStackTrace(String stackTrace) {
        if (stackTrace == null || stackTrace.isEmpty()) {
            return null;
        }
        // 多个类型命中时，选匹配次数最多的
        ErrorType best = null;
        int bestCount = 0;
        for (ErrorType type : values()) {
            int count = 0;
            for (String keyword : type.stackTraceMatches) {
                if (stackTrace.contains(keyword)) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = type;
            }
        }
        return best;
    }

    /**
     * 从错误消息文本中匹配（最低优先级）
     */
    public static ErrorType fromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        ErrorType best = null;
        int bestCount = 0;
        for (ErrorType type : values()) {
            int count = 0;
            String lowerMessage = message.toLowerCase();
            for (String keyword : type.messageKeywords) {
                if (lowerMessage.contains(keyword.toLowerCase())) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = type;
            }
        }
        return best;
    }
}
