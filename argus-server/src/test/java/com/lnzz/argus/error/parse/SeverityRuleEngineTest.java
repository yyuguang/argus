package com.lnzz.argus.error.parse;

import com.lnzz.argus.error.parse.SeverityRuleEngine.SeverityResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SeverityRuleEngine - 严重度规则初判")
class SeverityRuleEngineTest {

    private final SeverityRuleEngine engine = new SeverityRuleEngine();

    @Test
    @DisplayName("NULL_POINTER 默认严重度 P2")
    void nullPointerDefaultP2() {
        SeverityResult result = engine.evaluate("NULL_POINTER", "DEV", false);
        assertEquals(SeverityLevel.P2, result.severity());
    }

    @Test
    @DisplayName("SQL_EXCEPTION 默认 P1")
    void sqlExceptionP1() {
        SeverityResult result = engine.evaluate("SQL_EXCEPTION", "DEV", false);
        assertEquals(SeverityLevel.P1, result.severity());
    }

    @Test
    @DisplayName("NGINX_502 → P1")
    void nginx502P1() {
        SeverityResult result = engine.evaluate("NGINX_502", "DEV", false);
        assertEquals(SeverityLevel.P1, result.severity());
        assertEquals(AnalysisDecision.MUST_ANALYZE, result.analysisDecision());
    }

    @Test
    @DisplayName("NGINX_503 → P1")
    void nginx503P1() {
        SeverityResult result = engine.evaluate("NGINX_503", "DEV", false);
        assertEquals(SeverityLevel.P1, result.severity());
    }

    @Test
    @DisplayName("NGINX_504 → P1")
    void nginx504P1() {
        SeverityResult result = engine.evaluate("NGINX_504", "DEV", false);
        assertEquals(SeverityLevel.P1, result.severity());
    }

    @Test
    @DisplayName("NGINX_499 → P2")
    void nginx499P2() {
        SeverityResult result = engine.evaluate("NGINX_499", "DEV", false);
        assertEquals(SeverityLevel.P2, result.severity());
    }

    @Test
    @DisplayName("NGINX_5XX → P1")
    void nginx5xxP1() {
        SeverityResult result = engine.evaluate("NGINX_5XX", "DEV", false);
        assertEquals(SeverityLevel.P1, result.severity());
    }

    @Test
    @DisplayName("NGINX_4XX → P3")
    void nginx4xxP3() {
        SeverityResult result = engine.evaluate("NGINX_4XX", "DEV", false);
        assertEquals(SeverityLevel.P3, result.severity());
        assertEquals(AnalysisDecision.AGGREGATE_ONLY, result.analysisDecision());
    }

    @Test
    @DisplayName("UNKNOWN 类型已有指纹 → P2（默认）")
    void unknownKnownFingerprintP2() {
        SeverityResult result = engine.evaluate("UNKNOWN", "DEV", false);
        assertEquals(SeverityLevel.P2, result.severity());
    }

    @Test
    @DisplayName("UNKNOWN + 新指纹 → P1（高优分析）")
    void unknownNewFingerprintP1() {
        SeverityResult result = engine.evaluate("UNKNOWN", "DEV", true);
        assertEquals(SeverityLevel.P1, result.severity());
        assertTrue(result.reason().contains("未知新异常"));
        assertEquals(AnalysisDecision.MUST_ANALYZE, result.analysisDecision());
    }

    @Test
    @DisplayName("PROD 环境下 CONNECTION_REFUSED 保持 P1（默认已是 P1）")
    void prodConnectionRefusedStillP1() {
        SeverityResult result = engine.evaluate("CONNECTION_REFUSED", "PROD", false);
        assertEquals(SeverityLevel.P1, result.severity());
    }

    @Test
    @DisplayName("PROD 环境下 TIMEOUT 从 P2 升级为 P1")
    void prodTimeoutUpgrade() {
        SeverityResult result = engine.evaluate("TIMEOUT", "PROD", false);
        assertEquals(SeverityLevel.P1, result.severity());
        assertTrue(result.reason().contains("PROD环境升级"));
    }

    @Test
    @DisplayName("BIZ_EXCEPTION → P3 + 条件分析")
    void bizExceptionP3() {
        SeverityResult result = engine.evaluate("BIZ_EXCEPTION", "DEV", false);
        assertEquals(SeverityLevel.P3, result.severity());
        assertTrue(result.reason().contains("业务异常"));
        assertEquals(AnalysisDecision.CONDITIONAL_ANALYZE, result.analysisDecision());
    }

    @Test
    @DisplayName("P0/P1 严重度 → MUST_ANALYZE")
    void p0p1MustAnalyze() {
        SeverityResult result = engine.evaluate("NGINX_502", "DEV", false);
        assertEquals(SeverityLevel.P1, result.severity());
        assertEquals(AnalysisDecision.MUST_ANALYZE, result.analysisDecision());
    }

    @Test
    @DisplayName("P2 已知指纹 → CONDITIONAL_ANALYZE")
    void p2KnownFingerprintConditional() {
        SeverityResult result = engine.evaluate("NULL_POINTER", "DEV", false);
        assertEquals(SeverityLevel.P2, result.severity());
        assertEquals(AnalysisDecision.CONDITIONAL_ANALYZE, result.analysisDecision());
    }

    @Test
    @DisplayName("新指纹 P2 → MUST_ANALYZE")
    void newFingerprintP2MustAnalyze() {
        SeverityResult result = engine.evaluate("NULL_POINTER", "DEV", true);
        assertEquals(SeverityLevel.P2, result.severity());
        assertEquals(AnalysisDecision.MUST_ANALYZE, result.analysisDecision());
    }

    @Test
    @DisplayName("非法的 errorType 字符串回退为 UNKNOWN")
    void invalidErrorTypeFallsBackToUnknown() {
        SeverityResult result = engine.evaluate("NON_EXISTENT_TYPE", "DEV", false);
        assertEquals(SeverityLevel.P2, result.severity());
        assertTrue(result.reason().contains("未知异常"));
    }

    @Test
    @DisplayName("规则初判置信度固定 0.80")
    void defaultConfidenceIs0_80() {
        SeverityResult result = engine.evaluate("TIMEOUT", "DEV", false);
        assertEquals(0.80, result.confidence(), 0.001);
    }
}
