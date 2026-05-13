package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import com.lnzz.argus.datamonitor.mapper.DataMonitorConfigMapper;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.ConnectionPoolSnapshotMapper;
import com.lnzz.argus.datamonitor.mapper.InterfaceLogTableConfigMapper;
import com.lnzz.argus.datamonitor.mapper.SlowLogConfigMapper;
import com.lnzz.argus.datamonitor.service.DataMonitorConfigService.DataMonitorConfigOverview;
import com.lnzz.argus.datamonitor.service.DataMonitorConfigService.DataMonitorConfigUpdateRequest;
import com.lnzz.argus.datamonitor.service.impl.DataMonitorConfigServiceImpl;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("DataMonitorConfigService - SCM 应用联动监控配置")
class DataMonitorConfigServiceImplTest {

    @Test
    @DisplayName("未配置时返回绑定 SCM 应用映射的默认配置")
    void getOverviewReturnsDefaultConfigWhenMissing() {
        Fixture fixture = new Fixture();
        when(fixture.configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        DataMonitorConfigOverview overview = fixture.service.getOverview(1L, 2L);

        assertEquals(1L, overview.scmConfigId());
        assertEquals(2L, overview.mappingId());
        assertEquals("oms-product", overview.appName());
        assertEquals("PROD", overview.environment());
        assertFalse(overview.enabled());
        assertEquals("SCM_CONFIG", overview.alertWebhookMode());
        assertEquals(0, overview.datasourceCount());
        assertEquals(0, overview.logTableCount());
        verify(fixture.configMapper, never()).insert(any(DataMonitorConfig.class));
    }

    @Test
    @DisplayName("保存时创建应用级数据监控配置并绑定 ProjectMapping 与 ScmConfig")
    void saveOrUpdateCreatesConfigBoundToMappingAndScm() {
        Fixture fixture = new Fixture();
        when(fixture.configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        DataMonitorConfigUpdateRequest request = new DataMonitorConfigUpdateRequest(
                true,
                "OMS研发组",
                "zhangsan",
                "scm_config",
                "生产 OMS 数据库监控"
        );

        DataMonitorConfigOverview overview = fixture.service.saveOrUpdate(1L, 2L, request);

        assertEquals(1L, overview.scmConfigId());
        assertEquals(2L, overview.mappingId());
        assertEquals("OMS研发组", overview.ownerTeam());
        assertEquals("zhangsan", overview.techOwner());
        assertEquals("SCM_CONFIG", overview.alertWebhookMode());
        verify(fixture.configMapper).insert(any(DataMonitorConfig.class));
    }

    @Test
    @DisplayName("mappingId 不属于 scmConfigId 时拒绝保存")
    void saveOrUpdateRejectsMappingOutsideScmConfig() {
        Fixture fixture = new Fixture();
        fixture.mapping.setScmProjectId(200L);

        BizException exception = assertThrows(BizException.class,
                () -> fixture.service.saveOrUpdate(1L, 2L,
                        new DataMonitorConfigUpdateRequest(true, null, null, null, null)));

        assertEquals("应用映射不属于当前 SCM 配置", exception.getMessage());
        verify(fixture.configMapper, never()).insert(any(DataMonitorConfig.class));
    }

    private static class Fixture {
        private final DataMonitorConfigMapper configMapper = mock(DataMonitorConfigMapper.class);
        private final DataSourceConfigMapper dataSourceConfigMapper = mock(DataSourceConfigMapper.class);
        private final SlowLogConfigMapper slowLogConfigMapper = mock(SlowLogConfigMapper.class);
        private final ConnectionPoolSnapshotMapper connectionPoolSnapshotMapper = mock(ConnectionPoolSnapshotMapper.class);
        private final InterfaceLogTableConfigMapper interfaceLogTableConfigMapper = mock(InterfaceLogTableConfigMapper.class);
        private final ProjectMappingMapper projectMappingMapper = mock(ProjectMappingMapper.class);
        private final ScmConfigService scmConfigService = mock(ScmConfigService.class);
        private final DataMonitorConfigServiceImpl service =
                new DataMonitorConfigServiceImpl(configMapper, dataSourceConfigMapper, slowLogConfigMapper,
                        connectionPoolSnapshotMapper, interfaceLogTableConfigMapper,
                        projectMappingMapper, scmConfigService);
        private final ScmConfig scmConfig = new ScmConfig();
        private final ProjectMapping mapping = new ProjectMapping();

        private Fixture() {
            scmConfig.setId(1L);
            scmConfig.setScmProvider("github");
            scmConfig.setProjectId(100L);
            mapping.setId(2L);
            mapping.setAppName("oms-product");
            mapping.setScmProvider("github");
            mapping.setScmProjectId(100L);
            when(scmConfigService.requireById(1L)).thenReturn(scmConfig);
            when(projectMappingMapper.selectById(2L)).thenReturn(mapping);
            when(dataSourceConfigMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(slowLogConfigMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(connectionPoolSnapshotMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(interfaceLogTableConfigMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        }
    }
}
