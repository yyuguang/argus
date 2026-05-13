package com.lnzz.argus.datamonitor.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.datamonitor.service.DbRuntimeCollectService;
import com.lnzz.argus.datamonitor.service.DbRuntimeCollectService.DatasourceCollectResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MySQL 运行现场采集内部 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/internal/data-monitor/runtime")
@RequiredArgsConstructor
public class DbRuntimeCollectController {

    private final DbRuntimeCollectService collectService;

    @PostMapping("/collect")
    public Result<List<DatasourceCollectResult>> collectAllEnabled() {
        return Result.success(collectService.collectAllEnabled());
    }

    @PostMapping("/datasources/{datasourceId}/collect")
    public Result<DatasourceCollectResult> collectDatasource(@PathVariable Long datasourceId) {
        return Result.success(collectService.collectDatasource(datasourceId));
    }
}
