package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.SlowSqlEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 慢 SQL 事件 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SlowSqlEventMapper extends BaseMapper<SlowSqlEvent> {
}
