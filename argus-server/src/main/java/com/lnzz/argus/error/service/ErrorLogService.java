package com.lnzz.argus.error.service;

import com.lnzz.argus.error.model.BatchLogRequest;
import com.lnzz.argus.error.model.ErrorLogEntry;

import java.util.Map;

/**
 * 错误日志接收服务接口
 * <p>负责 Agent 推送日志的幂等接收、校验与批量审计</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ErrorLogService {

    /**
     * 接收单条日志，幂等写入
     * <p>处理流程：日志来源校验 → 错误类型识别 → 异常栈解析 → 指纹去重 → 入库 → 触发异步AI分析</p>
     *
     * @param entry 日志条目（支持 APP_LOG / NGINX_ACCESS / NGINX_ERROR 三种来源）
     * @return 处理结果，包含 status（ACCEPTED / AGGREGATED / DUPLICATED）、errorEventId、fingerprint 等字段
     */
    Map<String, Object> receiveSingle(ErrorLogEntry entry);

    /**
     * 批量接收日志
     * <p>每条日志独立幂等处理，生成批次审计记录</p>
     *
     * @param request 批量推送请求，包含 agentId 和日志条目列表
     * @return 汇总结果，包含 batchId、acceptedCount、duplicatedCount、errorCount 和各条详情
     */
    Map<String, Object> receiveBatch(BatchLogRequest request);
}
