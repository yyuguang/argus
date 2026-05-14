package com.lnzz.argus.error.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StackTraceParser - 异常栈解析")
class StackTraceParserTest {

    private final StackTraceParser parser = new StackTraceParser();

    @Test
    @DisplayName("标准异常栈：解析出 className / methodName / fileName / lineNumber")
    void parseStandardStackTrace() {
        String stackTrace = """
                java.lang.NullPointerException: Cannot invoke "String.isEmpty()" because "name" is null
                \tat com.example.Service.doWork(Service.java:42)
                \tat com.example.Controller.handle(Controller.java:18)
                \tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)""";

        ParsedStackTrace result = parser.parse(stackTrace);

        assertTrue(result.isParsed());
        assertEquals("java.lang.NullPointerException", result.getPrimaryExceptionClass());
        assertEquals("Cannot invoke \"String.isEmpty()\" because \"name\" is null",
                result.getPrimaryExceptionMessage());

        StackFrame topFrame = result.getTopFrame();
        assertNotNull(topFrame);
        assertEquals("com.example.Service", topFrame.getClassName());
        assertEquals("doWork", topFrame.getMethodName());
        assertEquals("Service.java", topFrame.getFileName());
        assertEquals(42, topFrame.getLineNumber());
    }

    @Test
    @DisplayName("多层 Caused by：提取 rootCause")
    void parseCausedByChain() {
        String stackTrace = """
                org.springframework.web.client.HttpServerErrorException: 500 Internal Server Error
                \tat com.example.Proxy.call(Proxy.java:30)
                Caused by: java.lang.IllegalStateException: Session expired
                \tat com.example.Auth.check(Auth.java:55)
                Caused by: java.sql.SQLException: Connection pool exhausted
                \tat com.example.Pool.acquire(Pool.java:120)""";

        ParsedStackTrace result = parser.parse(stackTrace);

        assertTrue(result.isParsed());
        assertEquals("java.sql.SQLException", result.getRootCauseClass());
        assertEquals("Connection pool exhausted", result.getRootCauseMessage());

        List<String> chain = result.getExceptionChain();
        assertEquals(3, chain.size());
        assertEquals("org.springframework.web.client.HttpServerErrorException", chain.get(0));
        assertEquals("java.lang.IllegalStateException", chain.get(1));
        assertEquals("java.sql.SQLException", chain.get(2));
    }

    @Test
    @DisplayName("null 输入 → isParsed() = false")
    void nullInputNotParsed() {
        ParsedStackTrace result = parser.parse(null);
        assertFalse(result.isParsed());
        assertEquals("异常栈为空", result.getParseError());
    }

    @Test
    @DisplayName("空字符串 → isParsed() = false")
    void emptyInputNotParsed() {
        ParsedStackTrace result = parser.parse("");
        assertFalse(result.isParsed());
    }

    @Test
    @DisplayName("空白字符串 → isParsed() = false")
    void blankInputNotParsed() {
        ParsedStackTrace result = parser.parse("   \n  \t  ");
        assertFalse(result.isParsed());
    }

    @Test
    @DisplayName("不完整栈：尽可能解析已有信息")
    void partialStackParsesWhatItCan() {
        String stackTrace = """
                java.lang.IndexOutOfBoundsException: Index 5 out of bounds for length 3
                \tat com.example.ListUtil.get(ListUtil.java:99)""";

        ParsedStackTrace result = parser.parse(stackTrace);

        assertTrue(result.isParsed());
        assertEquals("java.lang.IndexOutOfBoundsException", result.getPrimaryExceptionClass());
        assertNotNull(result.getTopFrame());
        assertEquals("com.example.ListUtil", result.getTopFrame().getClassName());
    }

    @Test
    @DisplayName("Native Method 标记")
    void nativeMethodDetected() {
        String stackTrace = """
                java.lang.ThreadDeath
                \tat java.lang.Thread.stop(Thread.java:850)
                \tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)""";

        ParsedStackTrace result = parser.parse(stackTrace);
        assertTrue(result.isParsed());

        // 找 Native Method 帧
        StackFrame nativeFrame = result.getFrames().stream()
                .filter(StackFrame::isNativeMethod)
                .findFirst().orElse(null);
        assertNotNull(nativeFrame);
        assertEquals("sun.reflect.NativeMethodAccessorImpl", nativeFrame.getClassName());
    }

    @Test
    @DisplayName("Unknown Source 标记")
    void unknownSourceDetected() {
        String stackTrace = """
                java.lang.RuntimeException: test
                \tat com.example.Foo.bar(Unknown Source)""";

        ParsedStackTrace result = parser.parse(stackTrace);
        assertTrue(result.isParsed());

        StackFrame frame = result.getTopFrame();
        assertNotNull(frame);
        assertTrue(frame.isUnknownSource());
    }

    @Test
    @DisplayName("单层异常（无 Caused by）→ rootCause = primaryClass")
    void singleLayerRootCauseEqualsPrimary() {
        String stackTrace = """
                java.lang.ArithmeticException: / by zero
                \tat com.example.Calc.divide(Calc.java:15)""";

        ParsedStackTrace result = parser.parse(stackTrace);
        assertEquals("java.lang.ArithmeticException", result.getPrimaryExceptionClass());
        assertEquals("java.lang.ArithmeticException", result.getRootCauseClass());
    }

    @Test
    @DisplayName("超过 20 帧截断为前 20 帧")
    void maxFramesLimitedTo20() {
        StringBuilder sb = new StringBuilder("java.lang.RuntimeException: too many frames\n");
        for (int i = 0; i < 30; i++) {
            sb.append("\tat com.example.Class").append(i)
                    .append(".method(Class").append(i).append(".java:").append(i + 10).append(")\n");
        }

        ParsedStackTrace result = parser.parse(sb.toString());
        assertTrue(result.isParsed());
        assertEquals(20, result.getFrames().size());
    }
}
