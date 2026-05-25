package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.ConnectionPoolSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 连接池指标快照 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ConnectionPoolSnapshotMapper extends BaseMapper<ConnectionPoolSnapshot> {

    /**
     * 判断监控配置下是否存在连接池快照。
     *
     * @param monitorConfigId 监控配置 ID
     * @return true 表示存在连接池监控数据
     */
    default boolean existsByMonitorConfigId(Long monitorConfigId) {
        return selectCount(new LambdaQueryWrapper<ConnectionPoolSnapshot>()
                .eq(ConnectionPoolSnapshot::getMonitorConfigId, monitorConfigId)) > 0;
    }
}
