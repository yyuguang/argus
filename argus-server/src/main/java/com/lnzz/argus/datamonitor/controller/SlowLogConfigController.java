package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.SlowLogConfigService;
import com.lnzz.argus.datamonitor.service.SlowLogConfigService.SlowLogConfigRequest;
import com.lnzz.argus.datamonitor.service.SlowLogConfigService.SlowLogConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * slow log 接入配置 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/scm/configs/{scmConfigId}/app-mappings/{mappingId}/data-monitor/datasources/{datasourceId}/slow-log")
@RequiredArgsConstructor
public class SlowLogConfigController {

    private final SlowLogConfigService slowLogConfigService;

    @GetMapping
    public Result<SlowLogConfigResponse> get(@PathVariable Long scmConfigId,
                                             @PathVariable Long mappingId,
                                             @PathVariable Long datasourceId) {
        return Result.success(slowLogConfigService.get(scmConfigId, mappingId, datasourceId));
    }

    @PutMapping
    public Result<SlowLogConfigResponse> update(@PathVariable Long scmConfigId,
                                                @PathVariable Long mappingId,
                                                @PathVariable Long datasourceId,
                                                @RequestBody SlowLogConfigRequest request) {
        return Result.success("slow log 配置更新成功",
                slowLogConfigService.saveOrUpdate(scmConfigId, mappingId, datasourceId, request));
    }
}
