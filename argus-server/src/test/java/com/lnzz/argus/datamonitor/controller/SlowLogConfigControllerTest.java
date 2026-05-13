package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.SlowLogConfigService;
import com.lnzz.argus.datamonitor.service.SlowLogConfigService.SlowLogConfigRequest;
import com.lnzz.argus.datamonitor.service.SlowLogConfigService.SlowLogConfigResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SlowLogConfigController - slow log 配置 API")
class SlowLogConfigControllerTest {

    @Test
    @DisplayName("查询 slow log 配置透传路径参数")
    void getDelegatesToService() {
        SlowLogConfigService service = mock(SlowLogConfigService.class);
        when(service.get(1L, 2L, 100L)).thenReturn(response());
        SlowLogConfigController controller = new SlowLogConfigController(service);

        Result<SlowLogConfigResponse> result = controller.get(1L, 2L, 100L);

        assertEquals(100L, result.getData().datasourceId());
        verify(service).get(1L, 2L, 100L);
    }

    @Test
    @DisplayName("更新 slow log 配置透传请求体")
    void updateDelegatesToService() {
        SlowLogConfigService service = mock(SlowLogConfigService.class);
        SlowLogConfigRequest request = new SlowLogConfigRequest(true, "FILE_TAIL",
                "/var/lib/mysql/mysql-slow.log", "UTF-8", 1000L, true, 0L);
        when(service.saveOrUpdate(1L, 2L, 100L, request)).thenReturn(response());
        SlowLogConfigController controller = new SlowLogConfigController(service);

        Result<SlowLogConfigResponse> result = controller.update(1L, 2L, 100L, request);

        assertEquals("FILE_TAIL", result.getData().sourceType());
        verify(service).saveOrUpdate(1L, 2L, 100L, request);
    }

    private SlowLogConfigResponse response() {
        return new SlowLogConfigResponse(10L, 100L, true, "FILE_TAIL",
                "/var/lib/mysql/mysql-slow.log", "UTF-8", 1000L, true, 0L, null);
    }
}
