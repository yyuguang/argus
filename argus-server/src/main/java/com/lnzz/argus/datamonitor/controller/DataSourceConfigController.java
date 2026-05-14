package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.DataSourceConfigRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.DataSourceConfigResponse;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.DataSourceTestRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.EnableRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConfigService.ExistingDataSourceTestRequest;
import com.lnzz.argus.datamonitor.service.DataSourceConnectivityTester.DataSourceTestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SCM 应用联动下的数据源监控配置 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/scm/configs/{scmConfigId}/app-mappings/{mappingId}/data-monitor/datasources")
@RequiredArgsConstructor
public class DataSourceConfigController {

    private final DataSourceConfigService dataSourceConfigService;

    @GetMapping
    public Result<List<DataSourceConfigResponse>> list(@PathVariable Long scmConfigId,
                                                       @PathVariable Long mappingId) {
        return Result.success(dataSourceConfigService.list(scmConfigId, mappingId));
    }

    @PostMapping
    public Result<DataSourceConfigResponse> create(@PathVariable Long scmConfigId,
                                                   @PathVariable Long mappingId,
                                                   @RequestBody DataSourceConfigRequest request) {
        return Result.success("数据源配置创建成功",
                dataSourceConfigService.create(scmConfigId, mappingId, request));
    }

    @PutMapping("/{datasourceId}")
    public Result<DataSourceConfigResponse> update(@PathVariable Long scmConfigId,
                                                   @PathVariable Long mappingId,
                                                   @PathVariable Long datasourceId,
                                                   @RequestBody DataSourceConfigRequest request) {
        return Result.success("数据源配置更新成功",
                dataSourceConfigService.update(scmConfigId, mappingId, datasourceId, request));
    }

    @PutMapping("/{datasourceId}/enabled")
    public Result<DataSourceConfigResponse> setEnabled(@PathVariable Long scmConfigId,
                                                       @PathVariable Long mappingId,
                                                       @PathVariable Long datasourceId,
                                                       @RequestBody EnableRequest request) {
        return Result.success("数据源启停状态更新成功",
                dataSourceConfigService.setEnabled(scmConfigId, mappingId, datasourceId, request));
    }

    @PostMapping("/test")
    public Result<DataSourceTestResult> test(@PathVariable Long scmConfigId,
                                             @PathVariable Long mappingId,
                                             @RequestBody DataSourceTestRequest request) {
        return Result.success(dataSourceConfigService.test(scmConfigId, mappingId, request));
    }

    @PostMapping("/{datasourceId}/test")
    public Result<DataSourceTestResult> testExisting(@PathVariable Long scmConfigId,
                                                     @PathVariable Long mappingId,
                                                     @PathVariable Long datasourceId,
                                                     @RequestBody(required = false) ExistingDataSourceTestRequest request) {
        return Result.success(dataSourceConfigService.testExisting(scmConfigId, mappingId, datasourceId, request));
    }
}
