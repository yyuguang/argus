package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.LogQualityCheckService;
import com.lnzz.argus.datamonitor.service.LogQualityCheckService.LogQualityCheckResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LogQualityCheckController - 日志质量巡检 API")
class LogQualityCheckControllerTest {

    @Test
    @DisplayName("批量巡检启用日志表")
    void checkAllEnabledDelegatesToService() {
        LogQualityCheckService service = mock(LogQualityCheckService.class);
        when(service.checkAllEnabled()).thenReturn(List.of(response()));
        LogQualityCheckController controller = new LogQualityCheckController(service);

        Result<List<LogQualityCheckResponse>> result = controller.checkAllEnabled();

        assertEquals(1, result.getData().size());
        verify(service).checkAllEnabled();
    }

    @Test
    @DisplayName("巡检指定日志表配置")
    void checkConfigDelegatesToService() {
        LogQualityCheckService service = mock(LogQualityCheckService.class);
        when(service.checkConfig(3L)).thenReturn(response());
        LogQualityCheckController controller = new LogQualityCheckController(service);

        Result<LogQualityCheckResponse> result = controller.checkConfig(3L);

        assertEquals("api_call_log", result.getData().tableName());
        verify(service).checkConfig(3L);
    }

    private LogQualityCheckResponse response() {
        return new LogQualityCheckResponse(3L, 1000L, "api_call_log", true, 10, 0, 100, "A", "巡检完成");
    }
}
