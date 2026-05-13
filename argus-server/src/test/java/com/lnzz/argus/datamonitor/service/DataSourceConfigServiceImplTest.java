package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.CollectOptions;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.DataSourceConfigRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.DataSourceConfigResponse;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.DataSourceTestRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.EnableRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.ThresholdConfig;
import com.lnzz.argus.datamonitor.service.DataSourceConnectivityTester.DataSourceConnectionRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConnectivityTester.DataSourceTestResult;
import com.lnzz.argus.datamonitor.service.impl.DataSourceConfigServiceImpl;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DataSourceConfigService - 应用级只读数据源配置")
class DataSourceConfigServiceImplTest {

    @Test
    @DisplayName("创建数据源时绑定监控配置并加密密码")
    void createBindsMonitorConfigAndEncryptsPassword() {
        Fixture fixture = new Fixture();
        when(fixture.dataSourceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        DataSourceConfigResponse response = fixture.service.create(1L, 2L, request(true));

        assertEquals(10L, response.monitorConfigId());
        assertEquals(2L, response.mappingId());
        assertEquals("oms_master", response.datasourceCode());
        assertTrue(response.readonly());
        assertTrue(response.collectProcesslist());
        verify(fixture.dataSourceMapper).insert(any(DataSourceConfig.class));
        DataSourceConfig saved = fixture.insertedConfig;
        assertNotEquals("readonly_pwd", saved.getPasswordSecret());
        assertEquals("readonly_pwd", fixture.secretCodec.decrypt(saved.getPasswordSecret()));
    }

    @Test
    @DisplayName("拒绝保存非只读数据源配置")
    void createRejectsNonReadonlyDatasource() {
        Fixture fixture = new Fixture();

        BizException exception = assertThrows(BizException.class,
                () -> fixture.service.create(1L, 2L, request(false)));

        assertEquals("数据源账号必须标记为只读", exception.getMessage());
        verify(fixture.dataSourceMapper, never()).insert(any(DataSourceConfig.class));
    }

    @Test
    @DisplayName("同一应用下数据源编码必须唯一")
    void createRejectsDuplicatedDatasourceCode() {
        Fixture fixture = new Fixture();
        DataSourceConfig existing = new DataSourceConfig();
        existing.setId(99L);
        when(fixture.dataSourceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        BizException exception = assertThrows(BizException.class,
                () -> fixture.service.create(1L, 2L, request(true)));

        assertEquals("同一应用下数据源编码已存在: oms_master", exception.getMessage());
    }

    @Test
    @DisplayName("启停接口仅更新 enabled 字段")
    void setEnabledUpdatesDatasourceState() {
        Fixture fixture = new Fixture();
        DataSourceConfig datasource = datasource();
        when(fixture.dataSourceMapper.selectById(100L)).thenReturn(datasource);

        DataSourceConfigResponse response = fixture.service.setEnabled(1L, 2L, 100L, new EnableRequest(false));

        assertFalse(response.enabled());
        verify(fixture.dataSourceMapper).updateById(datasource);
    }

    @Test
    @DisplayName("只读连通性测试透传连接参数")
    void testDelegatesToConnectivityTester() {
        Fixture fixture = new Fixture();
        DataSourceTestResult testResult = new DataSourceTestResult(true, true, true, true,
                true, "5.7.44", "只读监控权限验证通过");
        when(fixture.connectivityTester.test(any(DataSourceConnectionRequest.class))).thenReturn(testResult);

        DataSourceTestResult response = fixture.service.test(1L, 2L,
                new DataSourceTestRequest("jdbc:mysql://127.0.0.1:3306/oms", "u", "p"));

        assertTrue(response.connected());
        assertTrue(response.readonlyVerified());
        verify(fixture.connectivityTester).test(any(DataSourceConnectionRequest.class));
    }

    private DataSourceConfigRequest request(boolean readonly) {
        return new DataSourceConfigRequest(
                "oms_master",
                "OMS主库",
                "MYSQL",
                "5.7",
                "jdbc:mysql://10.0.1.10:3306/oms",
                null,
                null,
                null,
                "argus_readonly",
                "readonly_pwd",
                readonly,
                true,
                new ThresholdConfig(5, 30, 5, 80),
                new CollectOptions(true, true, true, true, true, true)
        );
    }

    private static DataSourceConfig datasource() {
        DataSourceConfig datasource = new DataSourceConfig();
        datasource.setId(100L);
        datasource.setMonitorConfigId(10L);
        datasource.setProjectMappingId(2L);
        datasource.setDatasourceCode("oms_master");
        datasource.setReadonly(true);
        datasource.setEnabled(true);
        return datasource;
    }

    private static class Fixture {
        private final DataSourceConfigMapper dataSourceMapper = mock(DataSourceConfigMapper.class);
        private final DataMonitorConfigMapper monitorConfigMapper = mock(DataMonitorConfigMapper.class);
        private final ProjectMappingMapper projectMappingMapper = mock(ProjectMappingMapper.class);
        private final ScmConfigService scmConfigService = mock(ScmConfigService.class);
        private final DataSourceSecretCodec secretCodec = new DataSourceSecretCodec();
        private final DataSourceConnectivityTester connectivityTester = mock(DataSourceConnectivityTester.class);
        private final DataSourceConfigServiceImpl service = new DataSourceConfigServiceImpl(
                dataSourceMapper, monitorConfigMapper, projectMappingMapper, scmConfigService,
                secretCodec, connectivityTester);
        private DataSourceConfig insertedConfig;

        private Fixture() {
            ScmConfig scmConfig = new ScmConfig();
            scmConfig.setId(1L);
            scmConfig.setScmProvider("github");
            scmConfig.setProjectId(100L);
            ProjectMapping mapping = new ProjectMapping();
            mapping.setId(2L);
            mapping.setAppName("oms-product");
            mapping.setScmProvider("github");
            mapping.setScmProjectId(100L);
            DataMonitorConfig monitorConfig = new DataMonitorConfig();
            monitorConfig.setId(10L);
            monitorConfig.setScmConfigId(1L);
            monitorConfig.setProjectMappingId(2L);
            when(scmConfigService.requireById(1L)).thenReturn(scmConfig);
            when(projectMappingMapper.selectById(2L)).thenReturn(mapping);
            when(monitorConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(monitorConfig);
            when(dataSourceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(dataSourceMapper.insert(any(DataSourceConfig.class))).thenAnswer(invocation -> {
                insertedConfig = invocation.getArgument(0);
                insertedConfig.setId(100L);
                return 1;
            });
        }
    }
}
