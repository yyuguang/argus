package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.DataMonitorConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用级数据监控配置 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface DataMonitorConfigMapper extends BaseMapper<DataMonitorConfig> {
}
