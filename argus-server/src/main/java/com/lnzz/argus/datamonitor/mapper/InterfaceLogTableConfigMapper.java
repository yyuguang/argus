package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.InterfaceLogTableConfig;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 接口日志表配置 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface InterfaceLogTableConfigMapper extends BaseMapper<InterfaceLogTableConfig> {

    /**
     * 查询应用映射下的接口日志表配置。
     *
     * @param mappingId 应用映射 ID
     * @return 接口日志表配置列表
     */
    default List<InterfaceLogTableConfig> findByMappingId(Long mappingId) {
        return selectList(new LambdaQueryWrapper<InterfaceLogTableConfig>()
                .eq(InterfaceLogTableConfig::getProjectMappingId, mappingId)
                .orderByAsc(InterfaceLogTableConfig::getTableName)
                .orderByAsc(InterfaceLogTableConfig::getId));
    }

    /**
     * 查询应用映射下指定接口日志表配置。
     *
     * @param mappingId 应用映射 ID
     * @param configId  配置 ID
     * @return 接口日志表配置
     */
    default InterfaceLogTableConfig findByIdAndMappingId(Long mappingId, Long configId) {
        InterfaceLogTableConfig config = selectById(configId);
        if (config == null || mappingId == null || !mappingId.equals(config.getProjectMappingId())) {
            return null;
        }
        return config;
    }

    /**
     * 查询数据源下指定日志表配置。
     *
     * @param datasourceId 数据源 ID
     * @param tableName    日志表名
     * @return 接口日志表配置
     */
    default InterfaceLogTableConfig findByDatasourceAndTable(Long datasourceId, String tableName) {
        return selectOne(new LambdaQueryWrapper<InterfaceLogTableConfig>()
                .eq(InterfaceLogTableConfig::getDatasourceId, datasourceId)
                .eq(InterfaceLogTableConfig::getTableName, tableName)
                .last("LIMIT 1"));
    }

    /**
     * 统计应用映射下的接口日志表配置数量。
     *
     * @param mappingId 应用映射 ID
     * @return 配置数量
     */
    default long countByMappingId(Long mappingId) {
        return selectCount(new LambdaQueryWrapper<InterfaceLogTableConfig>()
                .eq(InterfaceLogTableConfig::getProjectMappingId, mappingId));
    }
}
