package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.DataSourceConfigRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.DataSourceConfigResponse;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.DataSourceTestRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.EnableRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConnectivityTester.DataSourceTestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DataSourceConfigController - 数据源配置 API")
class DataSourceConfigControllerTest {

    @Test
    @DisplayName("列表查询透传 SCM 配置与应用映射 ID")
    void listDelegatesToService() {
        DataSourceConfigService service = mock(DataSourceConfigService.class);
        when(service.list(1L, 2L)).thenReturn(List.of(response(true)));
        DataSourceConfigController controller = new DataSourceConfigController(service);

        Result<List<DataSourceConfigResponse>> result = controller.list(1L, 2L);

        assertEquals(1, result.getData().size());
        verify(service).list(1L, 2L);
    }

    @Test
    @DisplayName("创建数据源透传请求体")
    void createDelegatesToService() {
        DataSourceConfigService service = mock(DataSourceConfigService.class);
        DataSourceConfigRequest request = new DataSourceConfigRequest("oms_master", null,
                "MYSQL", "5.7", "jdbc:mysql://127.0.0.1:3306/oms", null, null,
                null, "u", "p", true, true, null, null);
        when(service.create(1L, 2L, request)).thenReturn(response(true));
        DataSourceConfigController controller = new DataSourceConfigController(service);

        Result<DataSourceConfigResponse> result = controller.create(1L, 2L, request);

        assertEquals("oms_master", result.getData().datasourceCode());
        verify(service).create(1L, 2L, request);
    }

    @Test
    @DisplayName("启停数据源透传状态")
    void setEnabledDelegatesToService() {
        DataSourceConfigService service = mock(DataSourceConfigService.class);
        EnableRequest request = new EnableRequest(false);
        when(service.setEnabled(1L, 2L, 100L, request)).thenReturn(response(false));
        DataSourceConfigController controller = new DataSourceConfigController(service);

        Result<DataSourceConfigResponse> result = controller.setEnabled(1L, 2L, 100L, request);

        assertEquals(false, result.getData().enabled());
        verify(service).setEnabled(1L, 2L, 100L, request);
    }

    @Test
    @DisplayName("测试数据源连通性透传参数")
    void testDelegatesToService() {
        DataSourceConfigService service = mock(DataSourceConfigService.class);
        DataSourceTestRequest request = new DataSourceTestRequest("jdbc:mysql://127.0.0.1:3306/oms", "u", "p");
        DataSourceTestResult testResult = new DataSourceTestResult(true, true, true, true,
                true, "5.7.44", "ok");
        when(service.test(1L, 2L, request)).thenReturn(testResult);
        DataSourceConfigController controller = new DataSourceConfigController(service);

        Result<DataSourceTestResult> result = controller.test(1L, 2L, request);

        assertTrue(result.getData().readonlyVerified());
        verify(service).test(1L, 2L, request);
    }

    private DataSourceConfigResponse response(boolean enabled) {
        return new DataSourceConfigResponse(100L, 10L, 2L, "oms_master", "OMS主库",
                "MYSQL", "5.7", "jdbc:mysql://127.0.0.1:3306/oms", "127.0.0.1",
                3306, "oms", "argus_readonly", true, enabled, true, true, true,
                true, true, true, null);
    }
}
