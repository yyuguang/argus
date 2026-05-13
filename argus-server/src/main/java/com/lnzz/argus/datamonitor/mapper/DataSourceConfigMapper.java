package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.DataSourceConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用级业务库只读数据源配置 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface DataSourceConfigMapper extends BaseMapper<DataSourceConfig> {
}
