package com.lnzz.argus.knowledge.service;

import com.lnzz.argus.error.entity.ErrorAnalysis;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.knowledge.entity.KnowledgeAudit;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;

import java.util.List;

/**
 * 知识库服务接口（M8）
 * <p>覆盖知识条目全生命周期：自动草稿 → 相似检索 → 人工确认/误报/忽略 → 白名单沉淀 → 操作留痕</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface KnowledgeService {

    // ======================== M8-A01: 草稿生成 ========================

    /**
     * 从 AI 分析结果自动生成知识条目草稿
     * <p>提取 ErrorEvent + ErrorAnalysis 中的错误模式、根因、修复建议等信息</p>
     * <p>同指纹或同应用同类型草稿已存在时回写发生次数，避免重复草稿</p>
     *
     * @param event    错误事件
     * @param analysis AI 分析结果
     * @return 生成的知识条目，若跳过则返回 null
     */
    KnowledgeEntry generateDraft(ErrorEvent event, ErrorAnalysis analysis);

    // ======================== M8-A02: 相似检索 ========================

    /**
     * 检索与错误事件相似的知识条目
     * <p>委托 KnowledgeMatcher 执行三层降级检索</p>
     *
     * @param event      错误事件
     * @param maxResults 最大返回数
     * @return 相似条目列表
     */
    List<KnowledgeEntry> findSimilar(ErrorEvent event, int maxResults);

    // ======================== M8-A03: 人工操作 ========================

    /**
     * 人工确认知识条目（DRAFT / FALSE_POSITIVE → CONFIRMED）
     *
     * @param entryId  条目ID
     * @param operator 操作人
     * @param comment  备注
     * @return 更新后的条目
     */
    KnowledgeEntry confirm(Long entryId, String operator, String comment);

    /**
     * 标记为误报（DRAFT / CONFIRMED → FALSE_POSITIVE）
     * <p>误报条目不会被相似检索返回，但保留在库中供后续参考</p>
     *
     * @param entryId  条目ID
     * @param operator 操作人
     * @param comment  备注（建议说明为什么判定为误报）
     * @return 更新后的条目
     */
    KnowledgeEntry markFalsePositive(Long entryId, String operator, String comment);

    /**
     * 忽略条目（任意状态 → OUTDATED）
     * <p>过期或不再需要的条目，不会被检索返回</p>
     *
     * @param entryId  条目ID
     * @param operator 操作人
     * @param comment  备注
     * @return 更新后的条目
     */
    KnowledgeEntry ignore(Long entryId, String operator, String comment);

    // ======================== M8-A05: 白名单管理 ========================

    /**
     * 提升为白名单（CONFIRMED → WHITELIST）
     * <p>白名单条目可抑制通知（在 NotificationService 静默逻辑中生效）</p>
     *
     * @param entryId  条目ID
     * @param operator 操作人
     * @return 更新后的条目
     */
    KnowledgeEntry promoteWhitelist(Long entryId, String operator);

    /**
     * 降级白名单（WHITELIST → CONFIRMED）
     *
     * @param entryId  条目ID
     * @param operator 操作人
     * @return 更新后的条目
     */
    KnowledgeEntry demoteWhitelist(Long entryId, String operator);

    /**
     * 查找白名单候选：已确认且发生次数超过阈值的条目
     *
     * @param minOccurrence 最小发生次数
     * @return 候选列表
     */
    List<KnowledgeEntry> findWhitelistCandidates(int minOccurrence);

    // ======================== 查询 ========================

    /**
     * 按ID查询
     */
    KnowledgeEntry getById(Long id);

    /**
     * 按状态查询
     */
    List<KnowledgeEntry> listByStatus(String status);

    /**
     * 按错误类型查询
     */
    List<KnowledgeEntry> listByErrorType(String errorType);

    /**
     * 按应用、错误类型和状态组合查询。
     */
    List<KnowledgeEntry> listEntries(String status, String errorType, String appName);

    // ======================== M8-A04: 操作留痕 ========================

    /**
     * 查询指定条目的所有操作记录
     *
     * @param entryId 条目ID
     * @return 操作记录列表，按时间倒序
     */
    List<KnowledgeAudit> getAuditLog(Long entryId);
}
