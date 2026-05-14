package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.DbMetricSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库运行指标快照 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface DbMetricSnapshotMapper extends BaseMapper<DbMetricSnapshot> {
}
