package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService.EnableRequest;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService.InterfaceLogTableConfigRequest;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService.InterfaceLogTableConfigResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InterfaceLogTableConfigController - 接口日志表配置 API")
class InterfaceLogTableConfigControllerTest {

    @Test
    @DisplayName("查询接口日志表配置")
    void listDelegatesToService() {
        InterfaceLogTableConfigService service = mock(InterfaceLogTableConfigService.class);
        when(service.list(1L, 2L)).thenReturn(List.of(response()));
        InterfaceLogTableConfigController controller = new InterfaceLogTableConfigController(service);

        Result<List<InterfaceLogTableConfigResponse>> result = controller.list(1L, 2L);

        assertEquals(1, result.getData().size());
        verify(service).list(1L, 2L);
    }

    @Test
    @DisplayName("新增接口日志表配置")
    void createDelegatesToService() {
        InterfaceLogTableConfigService service = mock(InterfaceLogTableConfigService.class);
        InterfaceLogTableConfigRequest request = null;
        when(service.create(1L, 2L, null)).thenReturn(response());
        InterfaceLogTableConfigController controller = new InterfaceLogTableConfigController(service);

        Result<InterfaceLogTableConfigResponse> result = controller.create(1L, 2L, request);

        assertEquals("api_call_log", result.getData().tableName());
        verify(service).create(1L, 2L, request);
    }

    @Test
    @DisplayName("启停接口日志表配置")
    void setEnabledDelegatesToService() {
        InterfaceLogTableConfigService service = mock(InterfaceLogTableConfigService.class);
        EnableRequest request = new EnableRequest(false);
        when(service.setEnabled(1L, 2L, 3L, request)).thenReturn(response());
        InterfaceLogTableConfigController controller = new InterfaceLogTableConfigController(service);

        controller.setEnabled(1L, 2L, 3L, request);

        verify(service).setEnabled(1L, 2L, 3L, request);
    }

    private InterfaceLogTableConfigResponse response() {
        return new InterfaceLogTableConfigResponse(3L, 10L, 100L, "oms-product", "PROD", "接口日志",
                "api_call_log", "id", "api_code", "start_time", "end_time", "response_body",
                "response_code", "request_id", "trace_id", "ID_INCREMENT", 300, null, true, Set.of());
    }
}
