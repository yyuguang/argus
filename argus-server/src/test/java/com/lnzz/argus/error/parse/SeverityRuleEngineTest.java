package com.lnzz.argus.error.parse;

import com.lnzz.argus.common.enums.AnalysisDecision;
import com.lnzz.argus.common.enums.SeverityLevel;
import com.lnzz.argus.config.ErrorProcessingProperties;
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

    @Test
    @DisplayName("配置关闭 P2 分析时回落为只聚合")
    void disabledP2AnalysisAggregatesOnly() {
        ErrorProcessingProperties properties = new ErrorProcessingProperties();
        properties.getAnalysis().setAnalyzeP2(false);
        SeverityRuleEngine configuredEngine = new SeverityRuleEngine(properties);

        SeverityResult result = configuredEngine.evaluate("NULL_POINTER", "DEV", false);

        assertEquals(SeverityLevel.P2, result.severity());
        assertEquals(AnalysisDecision.AGGREGATE_ONLY, result.analysisDecision());
    }

    @Test
    @DisplayName("高频 BizException 可从 P3 升级为 P2")
    void highFrequencyBizExceptionUpgrades() {
        SeverityResult result = engine.evaluate(new SeverityRuleEngine.SeverityContext(
                "BIZ_EXCEPTION", "DEV", false,
                12, "/api/order/create", "/api/order/create",
                "order-team", "order-service", null, "参数重复提交"));

        assertEquals(SeverityLevel.P2, result.severity());
        assertEquals(SeverityLevel.P3, result.initialSeverity());
        assertTrue(result.reason().contains("重复出现12次"));
    }

    @Test
    @DisplayName("核心链路命中可将业务异常升级为 P1")
    void coreLinkBizExceptionUpgradesToP1() {
        ErrorProcessingProperties properties = new ErrorProcessingProperties();
        properties.getSeverityPolicy().getCoreLinkUpgrade().getRequestUris().add("/api/pay");
        SeverityRuleEngine configuredEngine = new SeverityRuleEngine(properties);

        SeverityResult result = configuredEngine.evaluate(new SeverityRuleEngine.SeverityContext(
                "BIZ_EXCEPTION", "DEV", false,
                1, "/api/pay/submit", "/api/pay/submit",
                "pay-team", "pay-service", null, "支付失败"));

        assertEquals(SeverityLevel.P1, result.severity());
        assertTrue(result.reason().contains("核心链路"));
    }

    @Test
    @DisplayName("命中白名单知识可降级为 P3")
    void whitelistKnowledgeDowngradesToP3() {
        SeverityResult result = engine.evaluate(new SeverityRuleEngine.SeverityContext(
                "NGINX_502", "PROD", false,
                1, "/health", "/health",
                "platform", "gateway", "WHITELIST", "探活偶发 502"));

        assertEquals(SeverityLevel.P3, result.severity());
        assertTrue(result.reason().contains("白名单"));
    }

    @Test
    @DisplayName("UNKNOWN 新指纹即使命中低风险知识也必须高优分析")
    void unknownNewFingerprintIsProtectedFromKnowledgeDowngrade() {
        SeverityResult result = engine.evaluate(new SeverityRuleEngine.SeverityContext(
                "UNKNOWN", "DEV", true,
                1, "/api/unknown", "/api/unknown",
                "platform", "gateway", "KNOWN_LOW_RISK", "未识别异常"));

        assertEquals(SeverityLevel.P1, result.severity());
        assertEquals(AnalysisDecision.MUST_ANALYZE, result.analysisDecision());
        assertTrue(result.reason().contains("未知新异常"));
    }

    @Test
    @DisplayName("数据一致性关键词可升级为 P1")
    void dataConsistencyRiskUpgradesToP1() {
        SeverityResult result = engine.evaluate(new SeverityRuleEngine.SeverityContext(
                "BIZ_EXCEPTION", "DEV", false,
                1, "/api/stock/deduct", "/api/stock/deduct",
                "inventory-team", "inventory-service", null, "库存扣减失败"));

        assertEquals(SeverityLevel.P1, result.severity());
        assertTrue(result.reason().contains("数据一致性"));
    }

    @Test
    @DisplayName("配置默认等级矩阵可覆盖错误类型默认等级")
    void configuredDefaultSeverityOverridesMatrix() {
        ErrorProcessingProperties properties = new ErrorProcessingProperties();
        properties.getSeverityPolicy().getDefaultLevels().put("BIZ_EXCEPTION", "P2");
        SeverityRuleEngine configuredEngine = new SeverityRuleEngine(properties);

        SeverityResult result = configuredEngine.evaluate("BIZ_EXCEPTION", "DEV", false);

        assertEquals(SeverityLevel.P2, result.initialSeverity());
        assertEquals(SeverityLevel.P2, result.severity());
    }
}
