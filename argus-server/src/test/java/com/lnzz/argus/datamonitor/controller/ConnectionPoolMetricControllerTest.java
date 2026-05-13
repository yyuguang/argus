package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService.PoolMetricRequest;
import com.lnzz.argus.datamonitor.service.ConnectionPoolMetricService.PoolMetricResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ConnectionPoolMetricController - 连接池指标内部 API")
class ConnectionPoolMetricControllerTest {

    @Test
    @DisplayName("Token 正确时透传连接池指标")
    void ingestDelegatesWhenTokenValid() {
        ConnectionPoolMetricService service = mock(ConnectionPoolMetricService.class);
        PoolMetricRequest request = request();
        when(service.ingest(request)).thenReturn(new PoolMetricResponse(1L, true, "POOL_EXHAUSTED", "P1", "accepted"));
        ConnectionPoolMetricController controller = new ConnectionPoolMetricController(service);
        ReflectionTestUtils.setField(controller, "internalToken", "secret");

        Result<PoolMetricResponse> result = controller.ingest("secret", request);

        assertTrue(result.getData().riskDetected());
        verify(service).ingest(request);
    }

    @Test
    @DisplayName("Token 错误时拒绝上报")
    void ingestRejectsInvalidToken() {
        ConnectionPoolMetricService service = mock(ConnectionPoolMetricService.class);
        ConnectionPoolMetricController controller = new ConnectionPoolMetricController(service);
        ReflectionTestUtils.setField(controller, "internalToken", "secret");

        BizException exception = assertThrows(BizException.class,
                () -> controller.ingest("bad-token", request()));

        assertEquals("内部 API Token 无效", exception.getMessage());
    }

    private PoolMetricRequest request() {
        return new PoolMetricRequest("oms-product", "PROD", "oms_master", "HIKARI",
                20, 0, 20, 5, 100L, 2000L, 3L, 0L,
                LocalDateTime.of(2026, 5, 13, 10, 0));
    }
}
