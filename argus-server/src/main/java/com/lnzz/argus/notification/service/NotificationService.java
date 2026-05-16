package com.lnzz.argus.notification.service;

import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;

/**
 * 通知服务接口
 * <p>统一通知入口，支持评审通知、错误告警，含去重、路由、静默控制、失败重试全链路</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface NotificationService {

    /**
     * 发送代码评审结果通知
     * <p>评审通过走 default 通道，不通过走 critical 通道。同一任务 60s 内不重复发送</p>
     *
     * @param task  评审任务（含项目名、MR链接、分支信息等）
     * @param score 评分结果（总分、等级、是否通过、各维度扣分数量）
     * @return true 发送成功，false 发送失败（含全部重试仍失败）
     */
    boolean sendReviewNotification(ReviewTask task, ScoreCalculator.ScoreResult score);

    /**
     * 发送代码评审结果通知（支持仓库级 webhook 覆盖与评审配置）
     *
     * @param task 评审任务
     * @param score 评分结果
     * @param scmConfig SCM 配置
     * @param reviewConfig 评审配置
     * @return true 发送成功，false 发送失败
     */
    boolean sendReviewNotification(ReviewTask task, ScoreCalculator.ScoreResult score,
                                   ScmConfig scmConfig, ReviewConfig reviewConfig);

    /**
     * 发送错误告警通知（完整链路）
     * <p>处理流程：路由匹配 → 静默检查 → 模板构建 → 企微发送 → 飞书/钉钉（预留） → 记录落库</p>
     * <p>静默规则：</p>
     * <ul>
     *   <li>P0/P1 永不禁用（alwaysNotifyP0P1=true）</li>
     *   <li>同指纹在 fingerprintInterval 秒内不重复通知</li>
     *   <li>P3 独立静默间隔（p3Interval）</li>
     *   <li>全局每小时上限（globalMaxPerHour）</li>
     * </ul>
     * <p>重试策略：最多 3 次，退避间隔 30s/120s/300s</p>
     *
     * @param event    错误事件（含严重度、错误类型、指纹等路由和静默决策所需字段）
     * @param analysis AI 分析结果（含根因、修复建议、置信度等）
     * @return true 已发送成功，false 未满足发送条件或发送失败
     */
    boolean sendErrorAlert(ErrorEvent event, ErrorAnalysis analysis);

    /**
     * 兼容旧接口的错误告警（异步）
     * <p>使用基本字段构造虚拟 ErrorEvent/ErrorAnalysis，复用完整 sendErrorAlert 链路</p>
     *
     * @param appName      应用名称
     * @param errorType    错误类型（NULL_POINTER / HTTP_500 等）
     * @param errorMessage 错误消息
     * @param severity     严重度 P0/P1/P2/P3
     * @param rootCause    根因描述
     */
    void sendErrorAlertLegacy(String appName, String errorType, String errorMessage,
                              String severity, String rootCause);
}
