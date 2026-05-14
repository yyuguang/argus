package com.lnzz.argus.knowledge.service;

import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.knowledge.model.ErrorFingerprintSummary;

import java.util.List;

/**
 * 错误知识汇总查询服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ErrorKnowledgeSummaryService {

    List<ErrorFingerprintSummary> findHighFrequency(int hours, int minOccurrences, int limit);

    List<ErrorFingerprintSummary> findNewFingerprints(int hours, int limit);

    List<ErrorFingerprintSummary> findSurgingFingerprints(int hours, int minIncrease, int limit);

    List<KnowledgeEntry> findWhitelistCandidates(int minOccurrence);
}
