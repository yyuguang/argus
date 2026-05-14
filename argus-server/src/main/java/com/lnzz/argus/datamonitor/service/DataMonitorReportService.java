package com.lnzz.argus.datamonitor.service;

import com.lnzz.argus.datamonitor.entity.DataMonitorReport;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据监控报告服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface DataMonitorReportService {

    List<DataMonitorReport> generateDaily(LocalDate date);
}
