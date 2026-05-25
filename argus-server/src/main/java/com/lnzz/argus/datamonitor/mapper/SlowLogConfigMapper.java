package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.SlowLogConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MySQL slow log 接入配置 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SlowLogConfigMapper extends BaseMapper<SlowLogConfig> {

    /**
     * 判断应用映射下是否存在启用的 slow log 配置。
     *
     * @param mappingId 应用映射 ID
     * @return true 表示存在启用配置
     */
    default boolean existsEnabledByMappingId(Long mappingId) {
        Long count = countEnabledByMappingId(mappingId);
        return count != null && count > 0;
    }

    /**
     * 统计应用映射下启用的 slow log 配置数量。
     *
     * @param mappingId 应用映射 ID
     * @return 启用配置数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM argus_slow_log_config sl
            JOIN argus_data_source_config ds ON sl.datasource_id = ds.id
            WHERE ds.project_mapping_id = #{mappingId}
              AND sl.enabled = 1
            """)
    Long countEnabledByMappingId(@Param("mappingId") Long mappingId);
}
