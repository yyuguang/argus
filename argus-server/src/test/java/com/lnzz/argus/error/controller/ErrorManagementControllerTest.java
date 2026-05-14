package com.lnzz.argus.error.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.service.ErrorManagementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ErrorManagementController - 管理台接口")
class ErrorManagementControllerTest {

    @Test
    @DisplayName("错误列表返回分页结构")
    void listErrorsReturnsPagePayload() {
        ErrorManagementService service = mock(ErrorManagementService.class);
        ErrorManagementController controller = new ErrorManagementController(service);
        ErrorEvent event = new ErrorEvent();
        event.setId(1L);
        event.setAppName("order-service");
        Page<ErrorEvent> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(event));
        when(service.queryEvents(1, 10, "order-service", null, "P1", null, null)).thenReturn(page);

        Result<Map<String, Object>> result = controller.listErrors(
                1, 10, "order-service", null, "P1", null, null);

        assertEquals(1L, result.getData().get("total"));
        assertEquals(1L, result.getData().get("pageNo"));
        assertEquals(10L, result.getData().get("pageSize"));
    }

    @Test
    @DisplayName("人工操作端点转发到服务层")
    void manualActionsDelegateToService() {
        ErrorManagementService service = mock(ErrorManagementService.class);
        ErrorManagementController controller = new ErrorManagementController(service);
        ErrorEvent event = new ErrorEvent();
        event.setId(1L);
        event.setSeverity("P0");
        when(service.adjustSeverity(1L, "P0", "生产不可用")).thenReturn(event);

        ErrorManagementController.AdjustSeverityRequest request =
                new ErrorManagementController.AdjustSeverityRequest();
        request.setSeverity("P0");
        request.setReason("生产不可用");

        Result<ErrorEvent> result = controller.adjustSeverity(1L, request);

        assertEquals("P0", result.getData().getSeverity());
        verify(service).adjustSeverity(1L, "P0", "生产不可用");
    }
}
