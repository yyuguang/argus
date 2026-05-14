package com.lnzz.argus.knowledge.service.impl;

import com.lnzz.argus.error.mapper.ErrorEventMapper;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.knowledge.model.ErrorFingerprintSummary;
import com.lnzz.argus.knowledge.service.ErrorKnowledgeSummaryService;
import com.lnzz.argus.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 错误知识汇总查询服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ErrorKnowledgeSummaryServiceImpl implements ErrorKnowledgeSummaryService {

    private final ErrorEventMapper errorEventMapper;
    private final KnowledgeService knowledgeService;

    @Override
    public List<ErrorFingerprintSummary> findHighFrequency(int hours, int minOccurrences, int limit) {
        int safeHours = hours > 0 ? hours : 1;
        int safeMinOccurrences = minOccurrences > 0 ? minOccurrences : 5;
        return errorEventMapper.findHighFrequencyFingerprints(
                LocalDateTime.now().minusHours(safeHours), safeMinOccurrences, safeLimit(limit));
    }

    @Override
    public List<ErrorFingerprintSummary> findNewFingerprints(int hours, int limit) {
        int safeHours = hours > 0 ? hours : 1;
        return errorEventMapper.findNewFingerprints(
                LocalDateTime.now().minusHours(safeHours), safeLimit(limit));
    }

    @Override
    public List<ErrorFingerprintSummary> findSurgingFingerprints(int hours, int minIncrease, int limit) {
        int safeHours = hours > 0 ? hours : 1;
        LocalDateTime since = LocalDateTime.now().minusHours(safeHours);
        LocalDateTime previousSince = since.minusHours(safeHours);
        return errorEventMapper.findSurgingFingerprints(
                since, previousSince, minIncrease > 0 ? minIncrease : 5, safeLimit(limit));
    }

    @Override
    public List<KnowledgeEntry> findWhitelistCandidates(int minOccurrence) {
        return knowledgeService.findWhitelistCandidates(minOccurrence > 0 ? minOccurrence : 5);
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }
}
