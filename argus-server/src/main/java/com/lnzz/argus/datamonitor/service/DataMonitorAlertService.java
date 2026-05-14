package com.lnzz.argus.datamonitor.service;

import java.util.List;

/**
 * 数据监控告警服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface DataMonitorAlertService {

    List<DataMonitorAlertResult> alertPending();

    record DataMonitorAlertResult(
            String refType,
            Long refId,
            boolean sent,
            String message
    ) {
    }
}
