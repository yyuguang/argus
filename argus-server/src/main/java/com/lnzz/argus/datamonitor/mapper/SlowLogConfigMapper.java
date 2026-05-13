package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.SlowLogConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * MySQL slow log 接入配置 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface SlowLogConfigMapper extends BaseMapper<SlowLogConfig> {
}
