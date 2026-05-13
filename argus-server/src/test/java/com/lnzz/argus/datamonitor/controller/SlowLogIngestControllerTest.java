package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService.SlowLogIngestResult;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService.SlowLogPushRequest;
import com.lnzz.argus.datamonitor.service.SlowLogIngestService.SlowLogRawPushRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SlowLogIngestController - slow log 接入 API")
class SlowLogIngestControllerTest {

    @Test
    @DisplayName("结构化 slow log 上报透传请求体")
    void ingestDelegatesToService() {
        SlowLogIngestService service = mock(SlowLogIngestService.class);
        SlowLogPushRequest request = new SlowLogPushRequest("oms-product", "PROD", "oms_master",
                "MYSQL_SLOW_LOG", 5600L, 300L, 20L, 900000L,
                "select 1", LocalDateTime.now(), "key-1");
        when(service.ingest(request)).thenReturn(new SlowLogIngestResult(true, false, 1L, "fp", "ok"));
        SlowLogIngestController controller = new SlowLogIngestController(service);

        Result<SlowLogIngestResult> result = controller.ingest(request);

        assertTrue(result.getData().accepted());
        verify(service).ingest(request);
    }

    @Test
    @DisplayName("原始 slow log 片段上报透传请求体")
    void ingestRawDelegatesToService() {
        SlowLogIngestService service = mock(SlowLogIngestService.class);
        SlowLogRawPushRequest request = new SlowLogRawPushRequest("oms-product", "PROD",
                "oms_master", "# Time: 260513 10:00:00", 10L, "raw-1");
        when(service.ingestRaw(request)).thenReturn(new SlowLogIngestResult(true, false, 1L, "fp", "ok"));
        SlowLogIngestController controller = new SlowLogIngestController(service);

        Result<SlowLogIngestResult> result = controller.ingestRaw(request);

        assertTrue(result.getData().accepted());
        verify(service).ingestRaw(request);
    }
}
