package com.lnzz.argus.error.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * Agent 实例实体
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_agent_instance")
public class AgentInstance extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Agent 实例标识 */
    private String agentId;

    /** 所属应用名称 */
    private String appName;

    /** 主机名 */
    private String host;

    /** IP 地址 */
    private String ip;

    /** 部署环境: dev/test/prod */
    private String environment;

    /** Agent 版本号 */
    private String agentVersion;

    /** 采集日志源: APP_LOG/NGINX_ACCESS/NGINX_ERROR */
    private String logSources;

    /** 最近心跳时间 */
    private LocalDateTime lastHeartbeatAt;

    /** 状态: ONLINE/OFFLINE/DEGRADED */
    private String status;
}
