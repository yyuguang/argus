package com.lnzz.argus.error.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.error.entity.ErrorContextLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 错误上下文日志快照 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ErrorContextLogMapper extends BaseMapper<ErrorContextLog> {
}
