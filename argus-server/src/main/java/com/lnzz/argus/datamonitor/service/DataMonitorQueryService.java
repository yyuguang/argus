package com.lnzz.argus.datamonitor.service;

import com.lnzz.argus.datamonitor.model.DataMonitorDashboardResponse;
import com.lnzz.argus.datamonitor.model.LogQualityIssueView;
import com.lnzz.argus.datamonitor.model.PoolRiskView;
import com.lnzz.argus.datamonitor.model.SlowSqlEventView;

import java.util.List;

/**
 * 数据监控工作台查询服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface DataMonitorQueryService {

    DataMonitorDashboardResponse getDashboard(String appName, String environment, String window);

    List<SlowSqlEventView> listSlowSql(String appName, String environment, String window);

    List<PoolRiskView> listPoolRisks(String appName, String environment, String window);

    List<LogQualityIssueView> listLogQualityIssues(String appName, String environment, String window);
}
