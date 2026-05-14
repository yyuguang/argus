package com.lnzz.argus.datamonitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.datamonitor.entity.DataMonitorReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据监控报告 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface DataMonitorReportMapper extends BaseMapper<DataMonitorReport> {
}
