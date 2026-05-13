package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.DataMonitorConfigService;
import com.lnzz.argus.datamonitor.service.DataMonitorConfigService.DataMonitorConfigOverview;
import com.lnzz.argus.datamonitor.service.DataMonitorConfigService.DataMonitorConfigUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM 应用联动下的数据监控配置 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/scm/configs/{scmConfigId}/app-mappings/{mappingId}/data-monitor")
@RequiredArgsConstructor
public class DataMonitorConfigController {

    private final DataMonitorConfigService dataMonitorConfigService;

    @GetMapping
    public Result<DataMonitorConfigOverview> get(@PathVariable Long scmConfigId,
                                                 @PathVariable Long mappingId) {
        return Result.success(dataMonitorConfigService.getOverview(scmConfigId, mappingId));
    }

    @PutMapping
    public Result<DataMonitorConfigOverview> update(@PathVariable Long scmConfigId,
                                                    @PathVariable Long mappingId,
                                                    @RequestBody DataMonitorConfigUpdateRequest request) {
        return Result.success("数据监控配置更新成功",
                dataMonitorConfigService.saveOrUpdate(scmConfigId, mappingId, request));
    }
}
