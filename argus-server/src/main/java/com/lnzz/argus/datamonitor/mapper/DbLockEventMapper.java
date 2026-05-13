package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.DbLockEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库锁等待与阻塞事件 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface DbLockEventMapper extends BaseMapper<DbLockEvent> {
}
