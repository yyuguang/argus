package com.lnzz.argus.knowledge.service;

import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;

import java.util.List;

/**
 * 知识匹配器接口（M8-A02）
 * <p>检索与给定错误事件相似的知识条目，用于注入 AI 分析 Prompt 或推荐参考案例</p>
 * <p>当前仅作为向量知识库冷启动/关闭时的兜底能力保留</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Deprecated
public interface KnowledgeMatcher {

    /**
     * 检索与给定错误事件相似的知识条目
     * <p>三层降级策略：</p>
     * <ol>
     *   <li>精确指纹匹配（同指纹的已确认条目）</li>
     *   <li>同错误类型 + 同应用（同业务域的相似错误）</li>
     *   <li>同错误类型（跨应用通用经验）</li>
     * </ol>
     * <p>仅返回状态为 CONFIRMED 或 WHITELIST 的条目</p>
     *
     * @param event      错误事件
     * @param maxResults 最大返回数
     * @return 相似知识条目列表，按 occurrenceCount 降序
     */
    List<KnowledgeEntry> findSimilar(ErrorEvent event, int maxResults);
}
