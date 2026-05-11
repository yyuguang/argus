package com.lnzz.argus.error.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * Agent 心跳请求
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
public class AgentHeartbeatRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Agent 实例标识 */
    @NotBlank(message = "agentId 不能为空")
    private String agentId;

    /** 所属应用名称 */
    @NotBlank(message = "appName 不能为空")
    private String appName;

    /** 主机名 */
    private String host;

    /** IP 地址 */
    private String ip;

    /** 部署环境 */
    private String environment;

    /** Agent 版本号 */
    private String agentVersion;

    /** 采集日志源 */
    private String logSources;
}
