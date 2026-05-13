package com.lnzz.argus.error.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FingerprintGenerator - 错误指纹生成")
class FingerprintGeneratorTest {

    private final FingerprintGenerator generator = new FingerprintGenerator();

    @Test
    @DisplayName("相同输入 → 相同 SHA-256 指纹")
    void sameInputSameFingerprint() {
        String fp1 = generator.generate("myapp", "NULL_POINTER", "com.example.Service",
                "doWork", 42, "NullPointerException");
        String fp2 = generator.generate("myapp", "NULL_POINTER", "com.example.Service",
                "doWork", 42, "NullPointerException");
        assertEquals(fp1, fp2);
    }

    @Test
    @DisplayName("不同 appName → 不同指纹")
    void differentAppNameDifferentFingerprint() {
        String fp1 = generator.generate("app-a", "NULL_POINTER", "com.example.Service",
                "doWork", 42, "NullPointerException");
        String fp2 = generator.generate("app-b", "NULL_POINTER", "com.example.Service",
                "doWork", 42, "NullPointerException");
        assertNotEquals(fp1, fp2);
    }

    @Test
    @DisplayName("不同 errorType → 不同指纹")
    void differentErrorTypeDifferentFingerprint() {
        String fp1 = generator.generate("myapp", "NULL_POINTER", "com.example.Service",
                "doWork", 42, "NullPointerException");
        String fp2 = generator.generate("myapp", "SQL_EXCEPTION", "com.example.Service",
                "doWork", 42, "NullPointerException");
        assertNotEquals(fp1, fp2);
    }

    @Test
    @DisplayName("不同 className → 不同指纹")
    void differentClassNameDifferentFingerprint() {
        String fp1 = generator.generate("myapp", "NULL_POINTER", "com.example.Foo",
                "doWork", 42, "NullPointerException");
        String fp2 = generator.generate("myapp", "NULL_POINTER", "com.example.Bar",
                "doWork", 42, "NullPointerException");
        assertNotEquals(fp1, fp2);
    }

    @Test
    @DisplayName("指纹长度 = 64（SHA-256 hex）")
    void fingerprintLengthIs64() {
        String fp = generator.generate("myapp", "NULL_POINTER", "com.example.Service",
                "doWork", 42, "NullPointerException");
        assertEquals(64, fp.length());
    }

    @Test
    @DisplayName("指纹只包含十六进制字符")
    void fingerprintIsHexOnly() {
        String fp = generator.generate("myapp", "NULL_POINTER", "com.example.Service",
                "doWork", 42, "NullPointerException");
        assertTrue(fp.matches("^[0-9a-f]{64}$"));
    }

    @Test
    @DisplayName("null 参数用空字符串替代，不影响生成")
    void nullParametersHandled() {
        String fp = generator.generate("myapp", "NULL_POINTER", "com.example.Service",
                null, null, null);
        assertEquals(64, fp.length());
    }

    @Test
    @DisplayName("带 ParsedStackTrace 的重载方法使用解析后的根因")
    void withParsedStackTraceUsesRootCause() {
        ParsedStackTrace parsed = new ParsedStackTrace();
        parsed.setRootCauseClass("IllegalArgumentException");

        String fp1 = generator.generate("myapp", "NULL_POINTER", "com.example.Service",
                "doWork", 42, null, parsed);
        String fp2 = generator.generate("myapp", "NULL_POINTER", "com.example.Service",
                "doWork", 42, "IllegalArgumentException");
        assertEquals(fp1, fp2);
    }

    @Test
    @DisplayName("rootCauseClass 为 null 时降级使用 errorType")
    void nullRootCauseFallsBackToErrorType() {
        String fp1 = generator.generate("myapp", "NULL_POINTER", "com.example.Service",
                "doWork", 42, null);
        // rootCauseClass 为 null 时，使用 errorType 作为最后一个字段
        String fp2 = generator.generate("myapp", "NULL_POINTER", "com.example.Service",
                "doWork", 42, "NULL_POINTER");
        assertEquals(fp1, fp2);
    }

    @Test
    @DisplayName("应用日志指纹按环境隔离")
    void applicationFingerprintSeparatedByEnvironment() {
        String prod = generator.generateApplication("order-service", "PROD", "NULL_POINTER",
                "com.example.OrderService", "create", 42, "NullPointerException",
                "orderId=123456 创建订单失败");
        String test = generator.generateApplication("order-service", "TEST", "NULL_POINTER",
                "com.example.OrderService", "create", 42, "NullPointerException",
                "orderId=123456 创建订单失败");

        assertNotEquals(prod, test);
    }

    @Test
    @DisplayName("应用日志指纹归一化动态数字")
    void applicationFingerprintNormalizesDynamicNumbers() {
        String fp1 = generator.generateApplication("order-service", "PROD", "NULL_POINTER",
                "com.example.OrderService", "create", 42, "NullPointerException",
                "orderId=123456 创建订单失败");
        String fp2 = generator.generateApplication("order-service", "PROD", "NULL_POINTER",
                "com.example.OrderService", "create", 42, "NullPointerException",
                "orderId=987654 创建订单失败");

        assertEquals(fp1, fp2);
    }

    @Test
    @DisplayName("Nginx 指纹归一化 URI ID 和 query string")
    void nginxFingerprintNormalizesUriIdAndQueryString() {
        String fp1 = generator.generateNginx("order-service", "PROD", "NGINX_502",
                "/api/orders/123456?traceId=abc", 502, 502, "order-service:8080");
        String fp2 = generator.generateNginx("order-service", "PROD", "NGINX_502",
                "/api/orders/987654?traceId=xyz", 502, 502, "order-service:8080");

        assertEquals(fp1, fp2);
    }

}
