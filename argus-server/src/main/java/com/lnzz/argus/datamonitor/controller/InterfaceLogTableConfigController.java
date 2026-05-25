package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService.EnableRequest;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService.InterfaceLogTableConfigRequest;
import com.lnzz.argus.datamonitor.service.InterfaceLogTableConfigService.InterfaceLogTableConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 接口日志表质量巡检配置 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/scm/configs/{scmConfigId}/app-mappings/{mappingId}/data-monitor/log-tables")
@RequiredArgsConstructor
public class InterfaceLogTableConfigController {

    private final InterfaceLogTableConfigService configService;

    @GetMapping
    public Result<List<InterfaceLogTableConfigResponse>> list(@PathVariable Long scmConfigId,
                                                              @PathVariable Long mappingId) {
        return Result.success(configService.list(scmConfigId, mappingId));
    }

    @PostMapping
    public Result<InterfaceLogTableConfigResponse> create(@PathVariable Long scmConfigId,
                                                          @PathVariable Long mappingId,
                                                          @RequestBody InterfaceLogTableConfigRequest request) {
        return Result.success(configService.create(scmConfigId, mappingId, request));
    }

    @PutMapping("/{configId}")
    public Result<InterfaceLogTableConfigResponse> update(@PathVariable Long scmConfigId,
                                                          @PathVariable Long mappingId,
                                                          @PathVariable Long configId,
                                                          @RequestBody InterfaceLogTableConfigRequest request) {
        return Result.success(configService.update(scmConfigId, mappingId, configId, request));
    }

    @PutMapping("/{configId}/enabled")
    public Result<InterfaceLogTableConfigResponse> setEnabled(@PathVariable Long scmConfigId,
                                                              @PathVariable Long mappingId,
                                                              @PathVariable Long configId,
                                                              @RequestBody EnableRequest request) {
        return Result.success(configService.setEnabled(scmConfigId, mappingId, configId, request));
    }

    @DeleteMapping("/{configId}")
    public Result<Map<String, Object>> delete(@PathVariable Long scmConfigId,
                                              @PathVariable Long mappingId,
                                              @PathVariable Long configId) {
        configService.delete(scmConfigId, mappingId, configId);
        return Result.success("接口日志表配置删除成功", Map.of("id", configId));
    }
}
