package com.lnzz.argus.error.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.error.service.AgentInstanceService;
import com.lnzz.argus.error.entity.AgentInstance;
import com.lnzz.argus.error.mapper.AgentInstanceMapper;
import com.lnzz.argus.error.metrics.ErrorLogMetrics;
import com.lnzz.argus.error.model.AgentHeartbeatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Agent 实例管理服务实现
 * <p>M4-A03: 负责 Agent 实例注册、心跳更新与状态管理</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentInstanceServiceImpl implements AgentInstanceService {

    private final AgentInstanceMapper agentInstanceMapper;
    private final ErrorLogMetrics metrics;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> heartbeat(AgentHeartbeatRequest request) {
        AgentInstance existing = agentInstanceMapper.selectOne(
                new LambdaQueryWrapper<AgentInstance>()
                        .eq(AgentInstance::getAgentId, request.getAgentId())
        );

        LocalDateTime now = LocalDateTime.now();
        String action;

        if (existing == null) {
            AgentInstance instance = new AgentInstance();
            instance.setAgentId(request.getAgentId());
            instance.setAppName(request.getAppName());
            instance.setHost(request.getHost());
            instance.setIp(request.getIp());
            instance.setEnvironment(request.getEnvironment());
            instance.setAgentVersion(request.getAgentVersion());
            instance.setLogSources(request.getLogSources() != null ? request.getLogSources() : "APP_LOG");
            instance.setLastHeartbeatAt(now);
            instance.setStatus("ONLINE");
            agentInstanceMapper.insert(instance);
            action = "REGISTERED";
            log.info("Agent 首次注册: agentId={}, appName={}, ip={}",
                    request.getAgentId(), request.getAppName(), request.getIp());
        } else {
            existing.setLastHeartbeatAt(now);
            existing.setStatus("ONLINE");
            if (request.getHost() != null) {
                existing.setHost(request.getHost());
            }
            if (request.getIp() != null) {
                existing.setIp(request.getIp());
            }
            if (request.getAgentVersion() != null) {
                existing.setAgentVersion(request.getAgentVersion());
            }
            if (request.getLogSources() != null) {
                existing.setLogSources(request.getLogSources());
            }
            agentInstanceMapper.updateById(existing);
            action = "HEARTBEAT_UPDATED";
            log.debug("Agent 心跳更新: agentId={}, appName={}", request.getAgentId(), request.getAppName());
        }

        metrics.recordAgentHeartbeat(request.getAgentId());

        return Map.of(
                "agentId", request.getAgentId(),
                "action", action,
                "status", "ONLINE",
                "heartbeatAt", now.toString()
        );
    }
}
