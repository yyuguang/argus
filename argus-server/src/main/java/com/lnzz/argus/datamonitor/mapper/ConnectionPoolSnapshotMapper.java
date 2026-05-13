package com.lnzz.argus.datamonitor.mapper;

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
}
