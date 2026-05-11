package com.lnzz.argus.error.service;

import com.lnzz.argus.error.entity.ErrorAnalysis;

/**
 * 错误分析编排服务接口
 * <p>串联 源码定位 → 历史案例查询 → Prompt构建 → AI分析 → 严重度校准 → 自动通知 全流程</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ErrorAnalysisService {

    /**
     * 对指定 ErrorEvent 执行 AI 分析（异步）
     * <p>完整流程：源码定位 → 历史相似案例查询 → Prompt 构建 → AI 分析（含重试降级） → 结果落库 → 严重度AI校准 → 自动推送通知</p>
     * <p>幂等保证：已分析过（analyzed=true）的事件不会重复分析</p>
     * <p>失败处理：分析失败也会标记 analyzed=true，避免无限重试，可通过人工入口补充</p>
     *
     * @param eventId 错误事件ID
     */
    void analyzeEvent(Long eventId);

    /**
     * 查询某事件的已有分析结果
     *
     * @param eventId 错误事件ID
     * @return 分析结果，未分析时返回 null
     */
    ErrorAnalysis getAnalysisByEventId(Long eventId);

    /**
     * 人工补充/覆盖分析结论
     * <p>支持两种模式：</p>
     * <ul>
     *   <li><b>HYBRID 模式</b>：已有 AI 分析结果，人工在此基础上修正 rootCause、severity、fixDescription 等字段</li>
     *   <li><b>MANUAL 模式</b>：无 AI 分析结果（首次即人工分析），创建新的分析记录</li>
     * </ul>
     * <p>人工修正的严重度会同步回写 ErrorEvent（severitySource=MANUAL）</p>
     *
     * @param eventId          错误事件ID
     * @param rootCause        人工判定的根因（可选）
     * @param severity         人工判定的严重度 P0/P1/P2/P3（可选）
     * @param fixDescription   修复建议（可选）
     * @param preventionAdvice 预防措施（可选）
     * @return 更新或新建的分析记录
     */
    ErrorAnalysis supplementManual(Long eventId, String rootCause, String severity,
                                   String fixDescription, String preventionAdvice);
}
