package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.InterfaceLogTableConfig;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.InterfaceLogTableConfigMapper;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService.InterfaceLogTableConfigRequest;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService.InterfaceLogTableConfigResponse;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableInspector.LogQualityRules;
import com.lnzz.argus.datamonitor.service.impl.InterfaceLogTableConfigServiceImpl;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("InterfaceLogTableConfigService - 接口日志表配置")
class InterfaceLogTableConfigServiceImplTest {

    @Test
    @DisplayName("创建任意字段映射日志表配置，不绑定 gaea_api_history")
    void createConfigWithCustomTableMapping() {
        Fixture fixture = new Fixture();

        InterfaceLogTableConfigResponse response = fixture.service.create(1L, 2L, request("api_call_log"));

        assertEquals("api_call_log", response.tableName());
        assertEquals("ID_INCREMENT", response.scanMode());
        ArgumentCaptor<InterfaceLogTableConfig> configCaptor = ArgumentCaptor.forClass(InterfaceLogTableConfig.class);
        verify(fixture.configMapper).insert(configCaptor.capture());
        verify(fixture.inspector).validateMapping(any(DataSourceConfig.class), any(), any(InterfaceLogTableConfig.class));
        assertEquals("oms-product", configCaptor.getValue().getAppName());
    }

    @Test
    @DisplayName("非法表名会被拒绝")
    void createRejectsUnsafeTableName() {
        Fixture fixture = new Fixture();

        BizException exception = assertThrows(BizException.class,
                () -> fixture.service.create(1L, 2L, request("api_call_log;drop")));

        assertEquals("日志表名非法: api_call_log;drop", exception.getMessage());
    }

    private InterfaceLogTableConfigRequest request(String tableName) {
        return new InterfaceLogTableConfigRequest(100L, "OMS接口日志", tableName, "id", "api_code",
                "start_time", "end_time", "response_body", "response_code", "request_id", "trace_id",
                "ID_INCREMENT", 300, true,
                new LogQualityRules(Set.of("api_code", "start_time", "end_time", "response_body"),
                        10, 5, 1, 512, 1000000L, Set.of("200", "0")),
                null);
    }

    private static class Fixture {
        private final InterfaceLogTableConfigMapper configMapper = mock(InterfaceLogTableConfigMapper.class);
        private final DataMonitorConfigMapper monitorConfigMapper = mock(DataMonitorConfigMapper.class);
        private final DataSourceConfigMapper dataSourceConfigMapper = mock(DataSourceConfigMapper.class);
        private final ProjectMappingMapper projectMappingMapper = mock(ProjectMappingMapper.class);
        private final ScmConfigService scmConfigService = mock(ScmConfigService.class);
        private final DataSourceSecretCodec secretCodec = new DataSourceSecretCodec();
        private final InterfaceLogTableInspector inspector = mock(InterfaceLogTableInspector.class);
        private final InterfaceLogTableConfigServiceImpl service = new InterfaceLogTableConfigServiceImpl(configMapper,
                monitorConfigMapper, dataSourceConfigMapper, projectMappingMapper, scmConfigService, secretCodec,
                inspector);

        private Fixture() {
            ScmConfig scmConfig = new ScmConfig();
            scmConfig.setId(1L);
            scmConfig.setScmProvider("github");
            scmConfig.setProjectId(200L);
            ProjectMapping mapping = new ProjectMapping();
            mapping.setId(2L);
            mapping.setAppName("oms-product");
            mapping.setScmProvider("github");
            mapping.setScmProjectId(200L);
            DataMonitorConfig monitorConfig = new DataMonitorConfig();
            monitorConfig.setId(10L);
            monitorConfig.setProjectMappingId(2L);
            monitorConfig.setScmConfigId(1L);
            monitorConfig.setAppName("oms-product");
            monitorConfig.setEnvironment("PROD");
            DataSourceConfig datasource = new DataSourceConfig();
            datasource.setId(100L);
            datasource.setProjectMappingId(2L);
            datasource.setMonitorConfigId(10L);
            datasource.setPasswordSecret("secret");
            when(scmConfigService.requireById(1L)).thenReturn(scmConfig);
            doReturn(mapping).when(projectMappingMapper).findById(2L);
            doReturn(monitorConfig).when(monitorConfigMapper).findByScmAndMapping(1L, 2L);
            doReturn(datasource).when(dataSourceConfigMapper).findByIdAndMappingId(2L, 100L);
            doReturn(null).when(configMapper).findByDatasourceAndTable(100L, "api_call_log");
        }
    }
}
