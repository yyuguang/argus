package com.lnzz.argus.knowledge.service;

import com.lnzz.argus.error.mapper.ErrorEventMapper;
import com.lnzz.argus.knowledge.entity.KnowledgeEntry;
import com.lnzz.argus.knowledge.model.ErrorFingerprintSummary;
import com.lnzz.argus.knowledge.service.impl.ErrorKnowledgeSummaryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ErrorKnowledgeSummaryService - 周期汇总查询")
class ErrorKnowledgeSummaryServiceTest {

    @Mock
    private ErrorEventMapper errorEventMapper;
    @Mock
    private KnowledgeService knowledgeService;

    private ErrorKnowledgeSummaryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ErrorKnowledgeSummaryServiceImpl(errorEventMapper, knowledgeService);
    }

    @Test
    @DisplayName("高频错误查询归一化窗口、阈值和 limit")
    void findHighFrequencyNormalizesArgs() {
        ErrorFingerprintSummary summary = new ErrorFingerprintSummary();
        summary.setErrorFingerprint("fp-001");
        when(errorEventMapper.findHighFrequencyFingerprints(any(LocalDateTime.class), eq(5), eq(20)))
                .thenReturn(List.of(summary));

        List<ErrorFingerprintSummary> result = service.findHighFrequency(0, 0, 0);

        assertEquals(1, result.size());
        assertEquals("fp-001", result.get(0).getErrorFingerprint());
    }

    @Test
    @DisplayName("新增指纹查询限制最大返回 100")
    void findNewFingerprintsCapsLimit() {
        service.findNewFingerprints(24, 1000);

        verify(errorEventMapper).findNewFingerprints(any(LocalDateTime.class), eq(100));
    }

    @Test
    @DisplayName("突增指纹查询使用当前窗口和上一窗口")
    void findSurgingFingerprintsUsesCurrentAndPreviousWindow() {
        service.findSurgingFingerprints(2, 0, 0);

        verify(errorEventMapper).findSurgingFingerprints(
                any(LocalDateTime.class), any(LocalDateTime.class), eq(5), eq(20));
    }

    @Test
    @DisplayName("白名单候选查询复用 KnowledgeService")
    void whitelistCandidatesDelegatesToKnowledgeService() {
        KnowledgeEntry entry = new KnowledgeEntry();
        entry.setId(1L);
        when(knowledgeService.findWhitelistCandidates(5)).thenReturn(List.of(entry));

        List<KnowledgeEntry> result = service.findWhitelistCandidates(0);

        assertEquals(1L, result.get(0).getId());
        verify(knowledgeService).findWhitelistCandidates(5);
    }
}
