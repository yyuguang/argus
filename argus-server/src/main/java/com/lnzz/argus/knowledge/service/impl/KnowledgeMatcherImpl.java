package com.lnzz.argus.knowledge.service.impl;

import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.knowledge.mapper.KnowledgeEntryMapper;
import com.lnzz.argus.knowledge.service.KnowledgeMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识匹配器 MySQL 实现
 * <p>三层降级检索：指纹 → 类型+应用 → 类型</p>
 * <p>仅在向量知识库冷启动或显式关闭时作为兜底逻辑使用</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Deprecated
public class KnowledgeMatcherImpl implements KnowledgeMatcher {

    private final KnowledgeEntryMapper knowledgeEntryMapper;

    @Override
    public List<KnowledgeEntry> findSimilar(ErrorEvent event, int maxResults) {
        List<KnowledgeEntry> results = new ArrayList<>();

        // 第1层：精确指纹匹配
        String fingerprint = event.getErrorFingerprint();
        if (fingerprint != null && !fingerprint.isEmpty()) {
            List<KnowledgeEntry> byFingerprint = knowledgeEntryMapper.findByFingerprint(fingerprint, maxResults);
            results.addAll(byFingerprint);
            if (results.size() >= maxResults) {
                return results.subList(0, maxResults);
            }
        }

        // 第2层：同错误类型 + 同应用
        String errorType = event.getErrorType();
        String appName = event.getAppName();
        if (errorType != null && appName != null) {
            int remaining = maxResults - results.size();
            List<KnowledgeEntry> byTypeAndApp = knowledgeEntryMapper.findByErrorTypeAndApp(
                    errorType, appName, remaining);
            for (KnowledgeEntry entry : byTypeAndApp) {
                if (results.stream().noneMatch(e -> e.getId().equals(entry.getId()))) {
                    results.add(entry);
                }
            }
            if (results.size() >= maxResults) {
                return results.subList(0, maxResults);
            }
        }

        // 第3层：同错误类型（跨应用）
        if (errorType != null) {
            int remaining = maxResults - results.size();
            List<KnowledgeEntry> byType = knowledgeEntryMapper.findByErrorType(errorType, remaining);
            for (KnowledgeEntry entry : byType) {
                if (results.stream().noneMatch(e -> e.getId().equals(entry.getId()))) {
                    results.add(entry);
                }
            }
        }

        return results;
    }
}
