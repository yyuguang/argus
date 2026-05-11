package com.lnzz.argus.error.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 错误日志接收链路 Prometheus 指标
 * <p>M4-A05: 覆盖接收、去重、异常、Agent 心跳各环节</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class ErrorLogMetrics {

    private final MeterRegistry meterRegistry;

    /** 在线 Agent 数量 */
    private final AtomicInteger onlineAgentCount = new AtomicInteger(0);

    /** 各 Agent 最近心跳时间戳 */
    private final Map<String, AtomicInteger> agentHeartbeatTimestamps = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Gauge.builder("argus_agent_online_count", onlineAgentCount, AtomicInteger::get)
                .description("在线 Agent 实例数")
                .register(meterRegistry);
    }

    /**
     * 记录日志接收成功
     */
    public void recordLogReceived(String appName, String logSource, String logLevel) {
        Counter.builder("argus_logs_received_total")
                .description("接收日志总数")
                .tags("app", nullToUnknown(appName),
                      "log_source", nullToUnknown(logSource),
                      "level", nullToUnknown(logLevel))
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录重复日志
     */
    public void recordLogDuplicated(String appName) {
        Counter.builder("argus_logs_duplicated_total")
                .description("重复日志总数")
                .tag("app", nullToUnknown(appName))
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录接收异常
     */
    public void recordLogError(String appName) {
        Counter.builder("argus_logs_receive_errors_total")
                .description("日志接收异常总数")
                .tag("app", nullToUnknown(appName))
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录批量推送规模分布
     */
    public void recordBatchSize(int size) {
        Timer.builder("argus_logs_batch_process")
                .description("批量日志处理耗时")
                .register(meterRegistry)
                .record(size, TimeUnit.MILLISECONDS);

        Counter.builder("argus_logs_batch_entries_total")
                .description("批量推送条目汇总")
                .register(meterRegistry)
                .increment(Math.max(size, 0));
    }

    /**
     * 更新 Agent 心跳时间戳
     */
    public void recordAgentHeartbeat(String agentId) {
        AtomicInteger ts = agentHeartbeatTimestamps.computeIfAbsent(agentId,
                k -> {
                    AtomicInteger newGauge = new AtomicInteger(0);
                    Gauge.builder("argus_agent_heartbeat_timestamp",
                                    newGauge, AtomicInteger::get)
                            .description("Agent 最近心跳时间戳")
                            .tag("agent_id", agentId)
                            .register(meterRegistry);
                    return newGauge;
                });
        ts.set((int) (System.currentTimeMillis() / 1000));
    }

    /**
     * 设置在线 Agent 数量
     */
    public void setOnlineAgentCount(int count) {
        onlineAgentCount.set(count);
    }

    private String nullToUnknown(String val) {
        return val != null && !val.isEmpty() ? val : "UNKNOWN";
    }
}
