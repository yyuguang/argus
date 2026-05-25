package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用级数据监控配置 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface DataMonitorConfigMapper extends BaseMapper<DataMonitorConfig> {

    /**
     * 按 SCM 配置和应用映射查询监控总配置。
     *
     * @param scmConfigId SCM 配置 ID
     * @param mappingId   应用映射 ID
     * @return 数据监控总配置
     */
    default DataMonitorConfig findByScmAndMapping(Long scmConfigId, Long mappingId) {
        return selectOne(new LambdaQueryWrapper<DataMonitorConfig>()
                .eq(DataMonitorConfig::getScmConfigId, scmConfigId)
                .eq(DataMonitorConfig::getProjectMappingId, mappingId)
                .last("LIMIT 1"));
    }

    /**
     * 按应用映射查询监控总配置。
     *
     * @param mappingId 应用映射 ID
     * @return 数据监控总配置
     */
    default DataMonitorConfig findByMappingId(Long mappingId) {
        return selectOne(new LambdaQueryWrapper<DataMonitorConfig>()
                .eq(DataMonitorConfig::getProjectMappingId, mappingId)
                .last("LIMIT 1"));
    }
}
