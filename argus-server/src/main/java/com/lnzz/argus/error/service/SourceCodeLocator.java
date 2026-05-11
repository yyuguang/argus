package com.lnzz.argus.error.service;

import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.entity.ProjectMapping;

import java.util.Map;

/**
 * 源码定位器接口（M5）
 * <p>将 ErrorEvent 中的错误信息映射到 SCM 仓库中的实际源码位置，供 AI 分析使用</p>
 * <p>支持多层降级策略：精确路径 → 候选文件 → 多模块 → Nginx入口降级</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface SourceCodeLocator {

    /**
     * 源码定位结果
     *
     * @param filePath     定位到的源码文件路径
     * @param content      文件内容
     * @param contextFiles 关联上下文文件（如 Mapper、Controller 等调用链相关文件）
     * @param mapping      匹配到的项目映射
     * @param found        是否定位成功
     * @param reason       失败原因（found=false 时填充）
     */
    record SourceLocation(
            String filePath,
            String content,
            Map<String, String> contextFiles,
            ProjectMapping mapping,
            boolean found,
            String reason
    ) {
        /** 快捷构造：定位失败 */
        public static SourceLocation notFound(String reason) {
            return new SourceLocation(null, null, Map.of(), null, false, reason);
        }
    }

    /**
     * 根据 appName 查询项目映射
     * <p>映射关系维护在 argus_project_mapping 表中，包含 SCM 平台、仓库ID、源码根路径、基础包名、默认分支</p>
     *
     * @param appName 应用名称（对应采集端上报的 appName）
     * @return 项目映射，未找到时返回 null
     */
    ProjectMapping resolveProjectMapping(String appName);

    /**
     * 对 ErrorEvent 执行完整的源码定位
     * <p>策略流程（按优先级递减）：</p>
     * <ol>
     *   <li><b>M5-A01 项目映射</b>：appName → ProjectMapping → ScmConfig</li>
     *   <li><b>M5-A02 模块匹配</b>：className → packageModuleMappings 匹配 → sourceRoot</li>
     *   <li><b>M5-A03 源码拉取</b>：通过 SCM API（GitLab/GitHub/Gitee）获取文件内容</li>
     *   <li><b>M5-A04 候选降级</b>：精确路径未命中时尝试 5 级降级（短名/全路径/多模块/文件名/原始路径）</li>
     *   <li><b>M5-A05 上下文代码</b>：获取关联类文件（Service→Mapper、Controller→Service 推断）</li>
     *   <li><b>M5-A06 入口路由</b>：Nginx 场景下 host/requestUri → appName 映射</li>
     *   <li><b>M5-A07 Nginx降级</b>：无 className 时降级到 application.yml 或入口 Controller</li>
     * </ol>
     *
     * @param event 错误事件（含 appName、className、filePath、sourceType 等）
     * @return 定位结果，found=false 时可通过 reason 查看失败原因
     */
    SourceLocation locate(ErrorEvent event);

    /**
     * 根据 host + requestUri 推断目标 appName（M5-A06）
     * <p>用于 Nginx 入口异常场景，当无法通过 className 定位时，反向从入口路由映射到目标服务</p>
     * <p>策略：upstreamAddr 提取服务名 → requestUri 关键字匹配已知项目</p>
     *
     * @param requestUri   请求 URI（如 /api/order/create）
     * @param upstreamAddr upstream 地址（如 order-service:8080）
     * @return 匹配到的 appName，未匹配返回 null
     */
    String resolveAppNameByRequestUri(String requestUri, String upstreamAddr);
}
