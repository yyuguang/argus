package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 应用级业务库只读数据源配置 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface DataSourceConfigMapper extends BaseMapper<DataSourceConfig> {

    /**
     * 查询应用映射下的数据源配置。
     *
     * @param mappingId 应用映射 ID
     * @return 数据源配置列表
     */
    default List<DataSourceConfig> findByMappingId(Long mappingId) {
        return selectList(new LambdaQueryWrapper<DataSourceConfig>()
                .eq(DataSourceConfig::getProjectMappingId, mappingId)
                .orderByAsc(DataSourceConfig::getDatasourceCode)
                .orderByAsc(DataSourceConfig::getId));
    }

    /**
     * 查询应用映射下指定数据源。
     *
     * @param mappingId     应用映射 ID
     * @param datasourceId  数据源 ID
     * @return 数据源配置
     */
    default DataSourceConfig findByIdAndMappingId(Long mappingId, Long datasourceId) {
        DataSourceConfig config = selectById(datasourceId);
        if (config == null || mappingId == null || !mappingId.equals(config.getProjectMappingId())) {
            return null;
        }
        return config;
    }

    /**
     * 查询应用映射下指定编码的数据源。
     *
     * @param mappingId       应用映射 ID
     * @param datasourceCode  数据源编码
     * @return 数据源配置
     */
    default DataSourceConfig findByCode(Long mappingId, String datasourceCode) {
        return selectOne(new LambdaQueryWrapper<DataSourceConfig>()
                .eq(DataSourceConfig::getProjectMappingId, mappingId)
                .eq(DataSourceConfig::getDatasourceCode, datasourceCode)
                .last("LIMIT 1"));
    }

    /**
     * 统计应用映射下的数据源数量。
     *
     * @param mappingId 应用映射 ID
     * @return 数据源数量
     */
    default long countByMappingId(Long mappingId) {
        return selectCount(new LambdaQueryWrapper<DataSourceConfig>()
                .eq(DataSourceConfig::getProjectMappingId, mappingId));
    }
}
