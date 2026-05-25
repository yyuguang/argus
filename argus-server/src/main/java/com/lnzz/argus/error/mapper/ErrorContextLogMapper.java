package com.lnzz.argus.error.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.error.entity.ErrorContextLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 错误上下文日志快照 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ErrorContextLogMapper extends BaseMapper<ErrorContextLog> {

    /**
     * 查询错误事件上下文日志。
     *
     * @param eventId 错误事件 ID
     * @param limit   最大返回数量
     * @return 上下文日志列表
     */
    default List<ErrorContextLog> findByEventId(Long eventId, int limit) {
        return selectList(new LambdaQueryWrapper<ErrorContextLog>()
                .eq(ErrorContextLog::getErrorEventId, eventId)
                .orderByAsc(ErrorContextLog::getLogTime)
                .last("LIMIT " + limit));
    }
}
