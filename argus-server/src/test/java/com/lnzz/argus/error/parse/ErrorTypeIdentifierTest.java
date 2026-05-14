package com.lnzz.argus.error.parse;

import com.lnzz.argus.common.enums.ErrorType;
import com.lnzz.argus.error.model.ErrorLogEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("ErrorTypeIdentifier - 错误类型识别")
class ErrorTypeIdentifierTest {

    private final ErrorTypeIdentifier identifier = new ErrorTypeIdentifier();

    @Test
    @DisplayName("className=NullPointerException → NULL_POINTER")
    void nullPointerByClassName() {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setClassName("java.lang.NullPointerException");
        assertEquals(ErrorType.NULL_POINTER, identifier.identify(entry));
    }

    @Test
    @DisplayName("className=SQLException → SQL_EXCEPTION")
    void sqlExceptionByClassName() {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setClassName("java.sql.SQLException");
        assertEquals(ErrorType.SQL_EXCEPTION, identifier.identify(entry));
    }

    @Test
    @DisplayName("className=HttpServerErrorException → HTTP_ERROR")
    void httpErrorByClassName() {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setClassName("org.springframework.web.client.HttpServerErrorException");
        assertEquals(ErrorType.HTTP_ERROR, identifier.identify(entry));
    }

    @Test
    @DisplayName("stackTrace 含 NullPointerException → NULL_POINTER")
    void nullPointerByStackTrace() {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setClassName("com.example.Service");
        entry.setStackTrace("at com.example.Service.doStuff(Service.java:42)\n" +
                "Caused by: java.lang.NullPointerException: Cannot invoke String.isEmpty()");
        assertEquals(ErrorType.NULL_POINTER, identifier.identify(entry));
    }

    @Test
    @DisplayName("message 含 SQLException 关键词 → SQL_EXCEPTION")
    void sqlExceptionByMessage() {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setClassName("com.example.Service");
        entry.setMessage("JDBC SQLException: ORA-00001 unique constraint violated");
        assertEquals(ErrorType.SQL_EXCEPTION, identifier.identify(entry));
    }

    @Test
    @DisplayName("无任何匹配 → UNKNOWN")
    void noMatchReturnsUnknown() {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setClassName("com.example.Service");
        entry.setMessage("something weird happened");
        assertEquals(ErrorType.UNKNOWN, identifier.identify(entry));
    }

    // ======================== Nginx HTTP 状态码识别 ========================

    @Test
    @DisplayName("Nginx httpStatus=502 → NGINX_502")
    void nginxHttp502() {
        assertEquals(ErrorType.NGINX_502, identifier.identifyNginxError(502));
    }

    @Test
    @DisplayName("Nginx httpStatus=503 → NGINX_503")
    void nginxHttp503() {
        assertEquals(ErrorType.NGINX_503, identifier.identifyNginxError(503));
    }

    @Test
    @DisplayName("Nginx httpStatus=504 → NGINX_504")
    void nginxHttp504() {
        assertEquals(ErrorType.NGINX_504, identifier.identifyNginxError(504));
    }

    @Test
    @DisplayName("Nginx httpStatus=499 → NGINX_499")
    void nginxHttp499() {
        assertEquals(ErrorType.NGINX_499, identifier.identifyNginxError(499));
    }

    @Test
    @DisplayName("Nginx httpStatus=500 → NGINX_5XX")
    void nginxHttp500() {
        assertEquals(ErrorType.NGINX_5XX, identifier.identifyNginxError(500));
    }

    @Test
    @DisplayName("Nginx httpStatus=501 → NGINX_5XX")
    void nginxHttp501() {
        assertEquals(ErrorType.NGINX_5XX, identifier.identifyNginxError(501));
    }

    @Test
    @DisplayName("Nginx httpStatus=404 → NGINX_4XX")
    void nginxHttp404() {
        assertEquals(ErrorType.NGINX_4XX, identifier.identifyNginxError(404));
    }

    @Test
    @DisplayName("Nginx httpStatus=403 → NGINX_4XX")
    void nginxHttp403() {
        assertEquals(ErrorType.NGINX_4XX, identifier.identifyNginxError(403));
    }

    @Test
    @DisplayName("Nginx httpStatus=200 → null（非异常）")
    void nginxHttp200Null() {
        assertNull(identifier.identifyNginxError(200));
    }

    @Test
    @DisplayName("Nginx httpStatus=null → null")
    void nginxHttpNull() {
        assertNull(identifier.identifyNginxError(null));
    }

    // ======================== 三参数重载 ========================

    @Test
    @DisplayName("三参数重载: className 匹配优先")
    void threeArgByClassName() {
        assertEquals(ErrorType.NULL_POINTER,
                identifier.identify("java.lang.NullPointerException", null, null));
    }

    @Test
    @DisplayName("三参数重载: stackTrace 匹配")
    void threeArgByStackTrace() {
        assertEquals(ErrorType.SQL_EXCEPTION,
                identifier.identify("com.example.Service",
                        "Caused by: java.sql.SQLException: connection timeout", null));
    }

    @Test
    @DisplayName("三参数重载: message 匹配")
    void threeArgByMessage() {
        assertEquals(ErrorType.TIMEOUT,
                identifier.identify("com.example.Service", null, "read timed out"));
    }
}
