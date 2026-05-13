package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.SlowSqlAnalysisService;
import com.lnzz.argus.datamonitor.service.SlowSqlAnalysisService.SlowSqlAnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SlowSqlAnalysisController - 慢 SQL 根因分析 API")
class SlowSqlAnalysisControllerTest {

    @Test
    @DisplayName("批量分析 PENDING 慢 SQL")
    void analyzePendingDelegatesToService() {
        SlowSqlAnalysisService service = mock(SlowSqlAnalysisService.class);
        when(service.analyzePending(10)).thenReturn(List.of(result()));
        SlowSqlAnalysisController controller = new SlowSqlAnalysisController(service);

        Result<List<SlowSqlAnalysisResult>> response = controller.analyzePending(10);

        assertEquals(1, response.getData().size());
        verify(service).analyzePending(10);
    }

    @Test
    @DisplayName("分析指定慢 SQL 事件")
    void analyzeEventDelegatesToService() {
        SlowSqlAnalysisService service = mock(SlowSqlAnalysisService.class);
        when(service.analyzeEvent(1L)).thenReturn(result());
        SlowSqlAnalysisController controller = new SlowSqlAnalysisController(service);

        Result<SlowSqlAnalysisResult> response = controller.analyzeEvent(1L);

        assertEquals("MISSING_INDEX", response.getData().causeType());
        verify(service).analyzeEvent(1L);
    }

    private SlowSqlAnalysisResult result() {
        return new SlowSqlAnalysisResult(1L, "DONE", "MISSING_INDEX", "P2", "分析完成");
    }
}
