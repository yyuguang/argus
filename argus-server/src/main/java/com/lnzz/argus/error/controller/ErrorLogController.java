package com.lnzz.argus.error.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.error.model.AgentHeartbeatRequest;
import com.lnzz.argus.error.model.BatchLogRequest;
import com.lnzz.argus.error.model.ErrorLogEntry;
import com.lnzz.argus.error.service.AgentInstanceService;
import com.lnzz.argus.error.service.ErrorLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent 日志推送接收控制器
 * <p>M4-A01/A02/A03: 接收日志推送、心跳管理，统一内部 API 入口</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
public class ErrorLogController {

    private final ErrorLogService errorLogService;
    private final AgentInstanceService agentInstanceService;

    // ======================== M4-A01/A02: 日志接收 ========================

    /**
     * 接收单条错误日志，幂等写入
     *
     * @param entry 日志条目
     * @return 接收结果: ACCEPTED / DUPLICATED
     */
    @PostMapping("/error-logs")
    public Result<Map<String, Object>> receiveSingle(@Valid @RequestBody ErrorLogEntry entry) {
        log.debug("收到单条日志推送: appName={}, logId={}, logSource={}, logLevel={}",
                entry.getAppName(), entry.getLogId(), entry.getLogSource(), entry.getLogLevel());

        Map<String, Object> receipt = errorLogService.receiveSingle(entry);
        return Result.success(receipt);
    }

    /**
     * 批量接收错误日志，每条独立幂等
     *
     * @param request 批量推送请求
     * @return 汇总结果: batchId / acceptedCount / duplicatedCount / errorCount
     */
    @PostMapping("/error-logs/batch")
    public Result<Map<String, Object>> receiveBatch(@Valid @RequestBody BatchLogRequest request) {
        log.info("收到批量日志推送: agentId={}, entryCount={}", request.getAgentId(), request.getEntries().size());

        Map<String, Object> receipt = errorLogService.receiveBatch(request);
        return Result.success(receipt);
    }

    // ======================== M4-A03: Agent 心跳 ========================

    /**
     * Agent 实例心跳上报，自动完成首次注册或心跳续期
     *
     * @param request 心跳请求
     * @return 处理结果: REGISTERED / HEARTBEAT_UPDATED
     */
    @PostMapping("/agents/heartbeat")
    public Result<Map<String, Object>> heartbeat(@Valid @RequestBody AgentHeartbeatRequest request) {
        log.debug("收到 Agent 心跳: agentId={}, appName={}", request.getAgentId(), request.getAppName());

        Map<String, Object> result = agentInstanceService.heartbeat(request);
        return Result.success(result);
    }
}
