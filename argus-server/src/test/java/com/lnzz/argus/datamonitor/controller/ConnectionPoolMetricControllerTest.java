package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService.PoolMetricRequest;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService.PoolMetricResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ConnectionPoolMetricController - 连接池指标内部 API")
class ConnectionPoolMetricControllerTest {

    @Test
    @DisplayName("透传连接池指标到业务服务")
    void ingestDelegatesToService() {
        ConnectionPoolMetricService service = mock(ConnectionPoolMetricService.class);
        PoolMetricRequest request = request();
        when(service.ingest(request)).thenReturn(new PoolMetricResponse(1L, true, "POOL_EXHAUSTED", "P1", "accepted"));
        ConnectionPoolMetricController controller = new ConnectionPoolMetricController(service);

        Result<PoolMetricResponse> result = controller.ingest(request);

        assertTrue(result.getData().riskDetected());
        verify(service).ingest(request);
    }

    private PoolMetricRequest request() {
        return new PoolMetricRequest("oms-product", "PROD", "oms_master", "HIKARI",
                20, 0, 20, 5, 100L, 2000L, 3L, 0L,
                LocalDateTime.of(2026, 5, 13, 10, 0));
    }
}
