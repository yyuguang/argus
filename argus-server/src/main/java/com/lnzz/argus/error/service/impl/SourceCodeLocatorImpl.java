package com.lnzz.argus.error.service.impl;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.error.service.SourceCodeLocator;
import com.lnzz.argus.scm.service.ScmPlatformService;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.error.entity.ErrorEvent;
import com.lnzz.argus.error.entity.ProjectMapping;
import com.lnzz.argus.error.mapper.ProjectMappingMapper;
import com.lnzz.argus.error.parse.SourceType;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.mapper.ScmConfigMapper;
import com.lnzz.argus.scm.service.ScmPlatformService;
import com.lnzz.argus.scm.service.ScmPlatformServiceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 源码定位器实现（M5）
 * <p>将 ErrorEvent 中的错误信息映射到 SCM 仓库中的源码位置，供 AI 分析使用</p>
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SourceCodeLocatorImpl implements SourceCodeLocator {

    private final ProjectMappingMapper projectMappingMapper;
    private final ScmConfigMapper scmConfigMapper;
    private final ScmPlatformServiceFactory scmFactory;

    // ======================== M5-A01: 项目映射查询 ========================

    @Override
    public ProjectMapping resolveProjectMapping(String appName) {
        ProjectMapping mapping = projectMappingMapper.selectOne(
                new LambdaQueryWrapper<ProjectMapping>()
                        .eq(ProjectMapping::getAppName, appName));
        if (mapping != null) {
            log.debug("项目映射已找到: appName={}, scmProvider={}, scmProjectId={}",
                    appName, mapping.getScmProvider(), mapping.getScmProjectId());
        }
        return mapping;
    }

    private ScmConfig resolveScmConfig(Long scmProjectId, String scmProvider) {
        return scmConfigMapper.selectOne(
                new LambdaQueryWrapper<ScmConfig>()
                        .eq(ScmConfig::getProjectId, scmProjectId)
                        .eq(ScmConfig::getScmProvider, scmProvider)
                        .eq(ScmConfig::getEnabled, true));
    }

    // ======================== 主入口 ========================

    @Override
    public SourceLocation locate(ErrorEvent event) {
        ProjectMapping mapping = resolveProjectMapping(event.getAppName());
        if (mapping == null) {
            return SourceLocation.notFound("未找到项目映射: appName=" + event.getAppName());
        }

        ScmConfig scmConfig = resolveScmConfig(mapping.getScmProjectId(), mapping.getScmProvider());
        if (scmConfig == null) {
            return SourceLocation.notFound("SCM 配置不可用: scmProjectId=" + mapping.getScmProjectId());
        }

        ScmPlatformService scmService = scmFactory.getRequired(mapping.getScmProvider());
        String ref = defaultRef(mapping, scmConfig);

        String sourceRoot = matchModule(event.getClassName(), mapping, scmConfig);

        String primaryPath = buildFilePath(event.getFilePath(), event.getClassName(),
                sourceRoot, mapping.getBasePackage());

        String content = scmService.getFileContent(scmConfig, primaryPath, ref);

        if (content == null) {
            String fallbackPath = tryCandidateFiles(scmService, scmConfig, event, mapping,
                    sourceRoot, ref);
            if (fallbackPath != null) {
                content = scmService.getFileContent(scmConfig, fallbackPath, ref);
                primaryPath = fallbackPath;
            }
        }

        Map<String, String> contextFiles = Collections.emptyMap();
        if (content != null) {
            contextFiles = fetchContextFiles(scmService, scmConfig, event, mapping,
                    sourceRoot, ref);
        }

        if (content != null) {
            log.info("源码定位成功: appName={}, filePath={}, contentLength={}",
                    event.getAppName(), primaryPath, content.length());
            return new SourceLocation(primaryPath, content, contextFiles, mapping, true, null);
        }

        if (SourceType.NGINX.getCode().equals(event.getSourceType())) {
            SourceLocation nginxLoc = nginxFallback(event, mapping);
            if (nginxLoc.found()) return nginxLoc;
        }

        return SourceLocation.notFound("源码定位失败: filePath=" + primaryPath);
    }

    // ======================== M5-A02: 模块匹配 ========================

    String matchModule(String className, ProjectMapping mapping, ScmConfig scmConfig) {
        String sourceRoot = mapping.getSourceRoot();
        if (sourceRoot == null || sourceRoot.isEmpty()) {
            sourceRoot = "src/main/java";
        }

        String packageModuleMappings = scmConfig.getPackageModuleMappings();
        if (className != null && packageModuleMappings != null && !packageModuleMappings.isEmpty()) {
            try {
                JSONArray mappings = JSON.parseArray(packageModuleMappings);
                for (int i = 0; i < mappings.size(); i++) {
                    var item = mappings.getJSONObject(i);
                    String pkgPrefix = item.getString("packagePrefix");
                    String moduleRoot = item.getString("sourceRoot");
                    if (pkgPrefix != null && moduleRoot != null && className.startsWith(pkgPrefix)) {
                        log.debug("模块匹配: className={} → sourceRoot={}", className, moduleRoot);
                        return moduleRoot;
                    }
                }
            } catch (Exception e) {
                log.debug("模块映射解析失败，使用默认 sourceRoot", e);
            }
        }

        String moduleSourceRoots = scmConfig.getModuleSourceRoots();
        if (moduleSourceRoots != null && !moduleSourceRoots.isEmpty()) {
            try {
                JSONArray roots = JSON.parseArray(moduleSourceRoots);
                if (!roots.isEmpty()) {
                    String basePackage = mapping.getBasePackage();
                    if (className != null && basePackage != null && className.startsWith(basePackage)) {
                        String subPkg = className.substring(basePackage.length());
                        if (subPkg.startsWith(".")) subPkg = subPkg.substring(1);
                        sourceRoot = roots.getString(0);
                    }
                }
            } catch (Exception e) {
                log.debug("模块源码根解析失败", e);
            }
        }

        return sourceRoot;
    }

    // ======================== M5-A04: 候选文件排序与降级 ========================

    private String tryCandidateFiles(ScmPlatformService scmService, ScmConfig scmConfig,
                                      ErrorEvent event, ProjectMapping mapping,
                                      String sourceRoot, String ref) {
        String className = event.getClassName();
        String filePath = event.getFilePath();
        String basePackage = mapping.getBasePackage();

        List<String> candidates = new ArrayList<>();

        if (className != null) {
            String simpleName = className.contains(".")
                    ? className.substring(className.lastIndexOf('.') + 1)
                    : className;
            String fqPath = className.replace('.', '/') + ".java";

            if (filePath != null && filePath.contains("/")) {
                candidates.add(sourceRoot + "/" + simpleName + ".java");
            }

            if (basePackage != null) {
                candidates.add(sourceRoot + "/" + fqPath);
            }

            String moduleSourceRoots = scmConfig.getModuleSourceRoots();
            if (moduleSourceRoots != null && !moduleSourceRoots.isEmpty()) {
                try {
                    JSONArray roots = JSON.parseArray(moduleSourceRoots);
                    for (int i = 0; i < roots.size(); i++) {
                        String altRoot = roots.getString(i);
                        if (!altRoot.equals(sourceRoot)) {
                            candidates.add(altRoot + "/" + fqPath);
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            if (filePath != null && filePath.contains("/")) {
                String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
                candidates.add(sourceRoot + "/" + simpleName + ".java");
            }
        }

        if (filePath != null && filePath.endsWith(".java")) {
            String cleanPath = filePath.startsWith("/") ? filePath.substring(1) : filePath;
            candidates.add(sourceRoot + "/" + cleanPath);
        }

        for (String candidate : candidates) {
            String content = scmService.getFileContent(scmConfig, candidate, ref);
            if (content != null) {
                log.info("候选文件匹配成功: candidate={}", candidate);
                return candidate;
            }
        }

        log.warn("所有候选文件均未找到: candidates={}", candidates);
        return null;
    }

    // ======================== M5-A05: 上下文代码补全 ========================

    private Map<String, String> fetchContextFiles(ScmPlatformService scmService, ScmConfig scmConfig,
                                                   ErrorEvent event, ProjectMapping mapping,
                                                   String sourceRoot, String ref) {
        Map<String, String> context = new LinkedHashMap<>();
        String basePackage = mapping.getBasePackage();
        if (basePackage == null) return context;

        int maxRelated = scmConfig.getMaxRelatedClasses() != null ? scmConfig.getMaxRelatedClasses() : 3;

        String className = event.getClassName();
        if (className != null && basePackage != null) {
            List<String> relatedPaths = inferRelatedClasses(className, basePackage, sourceRoot, maxRelated);
            for (String path : relatedPaths) {
                try {
                    String fileContent = scmService.getFileContent(scmConfig, path, ref);
                    if (fileContent != null) {
                        context.put(path, fileContent);
                    }
                } catch (Exception e) {
                    log.debug("上下文文件获取失败: path={}", path);
                }
            }
        }

        return context;
    }

    private List<String> inferRelatedClasses(String className, String basePackage,
                                              String sourceRoot, int maxCount) {
        List<String> paths = new ArrayList<>();
        if (className == null || !className.startsWith(basePackage)) return paths;

        String simpleName = className.contains(".")
                ? className.substring(className.lastIndexOf('.') + 1)
                : className;

        List<String> candidates = new ArrayList<>();
        if (simpleName.endsWith("ServiceImpl")) {
            String base = simpleName.replace("ServiceImpl", "");
            candidates.add(className.replace("ServiceImpl", "Mapper"));
            candidates.add(className.replace(".service.", ".mapper.").replace("ServiceImpl", "Mapper"));
            candidates.add(className.replace(".service.", ".repository.").replace("ServiceImpl", "Repository"));
        } else if (simpleName.endsWith("Controller")) {
            String base = simpleName.replace("Controller", "");
            candidates.add(className.replace("Controller", "Service"));
            candidates.add(className.replace(".controller.", ".service.").replace("Controller", "ServiceImpl"));
        } else if (simpleName.endsWith("Service")) {
            String base = simpleName.replace("Service", "");
            candidates.add(className.replace("Service", "Mapper"));
            candidates.add(className.replace(".service.", ".mapper.").replace("Service", "Mapper"));
        }

        for (int i = 0; i < Math.min(candidates.size(), maxCount); i++) {
            paths.add(sourceRoot + "/" + candidates.get(i).replace('.', '/') + ".java");
        }

        return paths;
    }

    // ======================== M5-A06: 入口路由映射 ========================

    @Override
    public String resolveAppNameByRequestUri(String requestUri, String upstreamAddr) {
        if (requestUri == null || requestUri.isEmpty()) return null;

        if (upstreamAddr != null && upstreamAddr.contains("-")) {
            String serviceName = upstreamAddr.contains(":")
                    ? upstreamAddr.substring(0, upstreamAddr.indexOf(':'))
                    : upstreamAddr;
            ProjectMapping mapping = resolveProjectMapping(serviceName);
            if (mapping != null) return serviceName;
        }

        List<ProjectMapping> allMappings = projectMappingMapper.selectList(new LambdaQueryWrapper<>());
        for (ProjectMapping m : allMappings) {
            String appName = m.getAppName();
            String keyword = appName.replace("-service", "").replace("-web", "").replace("-api", "");
            if (keyword.length() >= 3 && requestUri.toLowerCase().contains(keyword.toLowerCase())) {
                log.debug("URI 关键字匹配: uri={}, keyword={}, appName={}", requestUri, keyword, appName);
                return appName;
            }
        }

        return null;
    }

    // ======================== M5-A07: Nginx 降级策略 ========================

    private SourceLocation nginxFallback(ErrorEvent event, ProjectMapping mapping) {
        log.info("Nginx 入口异常降级定位: eventId={}", event.getId());

        ScmConfig scmConfig = resolveScmConfig(mapping.getScmProjectId(), mapping.getScmProvider());
        if (scmConfig == null) return SourceLocation.notFound("Nginx降级: SCM配置不可用");

        ScmPlatformService scmService = scmFactory.getRequired(mapping.getScmProvider());
        String ref = defaultRef(mapping, scmConfig);
        String sourceRoot = mapping.getSourceRoot() != null ? mapping.getSourceRoot() : "src/main/java";
        String basePackage = mapping.getBasePackage();

        List<String> fallbackPaths = new ArrayList<>();
        fallbackPaths.add("src/main/resources/application.yml");
        fallbackPaths.add("src/main/resources/application.properties");

        if (basePackage != null && event.getInterfaceRef() != null) {
            String uriPath = event.getInterfaceRef();
            String[] segments = uriPath.split("/");
            if (segments.length >= 3) {
                String module = segments[2];
                String controllerName = Character.toUpperCase(module.charAt(0))
                        + module.substring(1) + "Controller";
                fallbackPaths.add(sourceRoot + "/" + basePackage.replace('.', '/')
                        + "/controller/" + controllerName + ".java");
            }
        }

        for (String path : fallbackPaths) {
            String content = scmService.getFileContent(scmConfig, path, ref);
            if (content != null) {
                log.info("Nginx 降级成功: path={}", path);
                return new SourceLocation(path, content, Map.of(), mapping, true, "Nginx降级");
            }
        }

        return SourceLocation.notFound("Nginx降级: 所有候选路径均不可用");
    }

    // ======================== 辅助方法 ========================

    private String buildFilePath(String filePath, String className, String sourceRoot, String basePackage) {
        if (filePath != null && filePath.endsWith(".java")) {
            return sourceRoot + "/" + filePath.replace('\\', '/');
        }
        if (className != null) {
            return sourceRoot + "/" + className.replace('.', '/') + ".java";
        }
        return null;
    }

    private String defaultRef(ProjectMapping mapping, ScmConfig scmConfig) {
        return mapping.getDefaultBranch() != null ? mapping.getDefaultBranch() : "master";
    }
}
