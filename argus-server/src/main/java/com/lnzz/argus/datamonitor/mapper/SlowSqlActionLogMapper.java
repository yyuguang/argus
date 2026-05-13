package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.SlowSqlActionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 慢 SQL 人工处理日志 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SlowSqlActionLogMapper extends BaseMapper<SlowSqlActionLog> {
}
