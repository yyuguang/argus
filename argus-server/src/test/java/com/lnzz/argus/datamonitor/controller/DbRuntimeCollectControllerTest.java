package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.DbRuntimeCollectService;
import com.lnzz.argus.datamonitor.service.DbRuntimeCollectService.DatasourceCollectResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DbRuntimeCollectController - MySQL 运行现场采集 API")
class DbRuntimeCollectControllerTest {

    @Test
    @DisplayName("批量采集启用数据源")
    void collectAllEnabledDelegatesToService() {
        DbRuntimeCollectService service = mock(DbRuntimeCollectService.class);
        when(service.collectAllEnabled()).thenReturn(List.of(result(true)));
        DbRuntimeCollectController controller = new DbRuntimeCollectController(service);

        Result<List<DatasourceCollectResult>> response = controller.collectAllEnabled();

        assertEquals(1, response.getData().size());
        verify(service).collectAllEnabled();
    }

    @Test
    @DisplayName("采集指定数据源")
    void collectDatasourceDelegatesToService() {
        DbRuntimeCollectService service = mock(DbRuntimeCollectService.class);
        when(service.collectDatasource(100L)).thenReturn(result(true));
        DbRuntimeCollectController controller = new DbRuntimeCollectController(service);

        Result<DatasourceCollectResult> response = controller.collectDatasource(100L);

        assertTrue(response.getData().success());
        verify(service).collectDatasource(100L);
    }

    private DatasourceCollectResult result(boolean success) {
        return new DatasourceCollectResult(100L, "oms_master", success, 1000L,
                2, 1, 0, 0, 1, 1, 0, "采集成功");
    }
}
