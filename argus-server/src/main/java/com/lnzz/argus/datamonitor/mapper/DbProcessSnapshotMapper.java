package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.DbProcessSnapshot;
import org.apache.ibatis.annotations.Mapper;

/**
 * 当前执行 SQL 快照 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface DbProcessSnapshotMapper extends BaseMapper<DbProcessSnapshot> {
}
