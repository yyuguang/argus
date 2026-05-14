package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.DataMonitorConfigService;
import com.lnzz.argus.datamonitor.service.DataMonitorConfigService.DataMonitorConfigOverview;
import com.lnzz.argus.datamonitor.service.DataMonitorConfigService.DataMonitorConfigUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DataMonitorConfigController - 应用级数据监控配置 API")
class DataMonitorConfigControllerTest {

    @Test
    @DisplayName("查询应用数据监控配置时透传 scmConfigId 与 mappingId")
    void getDelegatesToService() {
        DataMonitorConfigService service = mock(DataMonitorConfigService.class);
        DataMonitorConfigOverview overview = overview();
        when(service.getOverview(1L, 2L)).thenReturn(overview);
        DataMonitorConfigController controller = new DataMonitorConfigController(service);

        Result<DataMonitorConfigOverview> result = controller.get(1L, 2L);

        assertEquals("oms-product", result.getData().appName());
        verify(service).getOverview(1L, 2L);
    }

    @Test
    @DisplayName("更新应用数据监控配置时透传请求体")
    void updateDelegatesToService() {
        DataMonitorConfigService service = mock(DataMonitorConfigService.class);
        DataMonitorConfigUpdateRequest request = new DataMonitorConfigUpdateRequest(
                true, "OMS研发组", "zhangsan", "SCM_CONFIG",
                30, 30, 300, 60, "remark");
        when(service.saveOrUpdate(1L, 2L, request)).thenReturn(overview());
        DataMonitorConfigController controller = new DataMonitorConfigController(service);

        Result<DataMonitorConfigOverview> result = controller.update(1L, 2L, request);

        assertEquals(2L, result.getData().mappingId());
        verify(service).saveOrUpdate(1L, 2L, request);
    }

    private DataMonitorConfigOverview overview() {
        return new DataMonitorConfigOverview(
                10L,
                1L,
                2L,
                "oms-product",
                "PROD",
                true,
                "OMS研发组",
                "zhangsan",
                "SCM_CONFIG",
                30,
                30,
                300,
                60,
                "remark",
                0,
                0,
                false,
                false
        );
    }
}
