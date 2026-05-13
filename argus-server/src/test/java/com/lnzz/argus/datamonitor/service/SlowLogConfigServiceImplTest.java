package com.lnzz.argus.datamonitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import com.lnzz.argus.datamonitor.entity.SlowLogConfig;
import com.lnzz.argus.datamonitor.mapper.DataSourceConfigMapper;
import com.lnzz.argus.datamonitor.mapper.SlowLogConfigMapper;
import com.lnzz.argus.datamonitor.service.SlowLogConfigService.SlowLogConfigRequest;
import com.lnzz.argus.datamonitor.service.SlowLogConfigService.SlowLogConfigResponse;
import com.lnzz.argus.datamonitor.service.impl.SlowLogConfigServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SlowLogConfigService - slow log 配置")
class SlowLogConfigServiceImplTest {

    @Test
    @DisplayName("未配置时返回绑定数据源的默认配置")
    void getReturnsDefaultWhenMissing() {
        Fixture fixture = new Fixture();
        when(fixture.slowLogConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        SlowLogConfigResponse response = fixture.service.get(1L, 2L, 100L);

        assertEquals(100L, response.datasourceId());
        assertFalse(response.enabled());
        assertEquals("FILE_TAIL", response.sourceType());
        assertEquals(1000L, response.minQueryTimeMs());
    }

    @Test
    @DisplayName("保存 slow log 配置时绑定应用数据源")
    void saveOrUpdateCreatesConfig() {
        Fixture fixture = new Fixture();
        when(fixture.slowLogConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        SlowLogConfigRequest request = new SlowLogConfigRequest(true, "file_tail",
                "/var/lib/mysql/mysql-slow.log", "UTF-8", 1500L, true, 10L);

        SlowLogConfigResponse response = fixture.service.saveOrUpdate(1L, 2L, 100L, request);

        assertTrue(response.enabled());
        assertEquals("FILE_TAIL", response.sourceType());
        assertEquals(1500L, response.minQueryTimeMs());
        verify(fixture.slowLogConfigMapper).insert(any(SlowLogConfig.class));
    }

    private static class Fixture {
        private final DataSourceConfigMapper dataSourceConfigMapper = mock(DataSourceConfigMapper.class);
        private final SlowLogConfigMapper slowLogConfigMapper = mock(SlowLogConfigMapper.class);
        private final SlowLogConfigServiceImpl service =
                new SlowLogConfigServiceImpl(dataSourceConfigMapper, slowLogConfigMapper);

        private Fixture() {
            DataSourceConfig datasource = new DataSourceConfig();
            datasource.setId(100L);
            datasource.setProjectMappingId(2L);
            when(dataSourceConfigMapper.selectById(100L)).thenReturn(datasource);
        }
    }
}
