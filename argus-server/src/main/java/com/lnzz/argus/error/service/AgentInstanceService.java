package com.lnzz.argus.error.service;

import com.lnzz.argus.error.model.AgentHeartbeatRequest;

import java.util.Map;

/**
 * Agent 实例管理服务接口
 * <p>管理集群内 Agent 采集端的注册、心跳续期与在线状态</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface AgentInstanceService {

    /**
     * 处理 Agent 心跳
     * <p>首次心跳自动注册，后续心跳续期并更新版本/日志来源等元信息</p>
     * <p>每次心跳同步更新 Prometheus 指标（agent_heartbeat_total）</p>
     *
     * @param request 心跳请求（含 agentId、appName、host、ip、environment、agentVersion、logSources）
     * @return 处理结果，包含 agentId、action（REGISTERED / HEARTBEAT_UPDATED）、status（ONLINE）、heartbeatAt
     */
    Map<String, Object> heartbeat(AgentHeartbeatRequest request);
}
