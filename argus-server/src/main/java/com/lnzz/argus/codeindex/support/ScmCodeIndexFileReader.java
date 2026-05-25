package com.lnzz.argus.codeindex.support;

import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.service.ScmPlatformService;
import com.lnzz.argus.scm.service.ScmPlatformServiceFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * @classname: ScmCodeIndexFileReader
 * @author: Fantasy
 * @date: 2026/05/19 17:40
 * @description: SCM 源码索引文件读取器，首期按已知文件路径定向读取并组装本地临时工作区。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScmCodeIndexFileReader {

    private final ScmPlatformServiceFactory scmPlatformServiceFactory;

    /**
     * 按已知路径读取 SCM 文件并写入临时工作区。
     *
     * @param scmConfig SCM 仓库配置
     * @param ref 分支或提交号
     * @param filePaths 文件路径集合
     * @return 临时工作区
     * @throws IOException 临时目录创建或文件写入失败时抛出
     */
    public ScmCodeIndexWorkspace readKnownFiles(ScmConfig scmConfig, String ref, Collection<String> filePaths) throws IOException {
        Path repositoryRoot = Files.createTempDirectory("argus-code-index-");
        ScmCodeIndexWorkspace workspace = new ScmCodeIndexWorkspace(repositoryRoot);
        if (scmConfig == null || isBlank(scmConfig.getScmProvider())) {
            workspace.getWarnings().add("SCM 配置缺失或 provider 为空");
            return workspace;
        }
        ScmPlatformService platformService = scmPlatformServiceFactory.getRequired(scmConfig.getScmProvider());
        readFilesIntoWorkspace(platformService, scmConfig, ref, filePaths, workspace);
        return workspace;
    }

    /**
     * 读取指定 ref 下的仓库文件树，并把满足条件的文件写入临时工作区。
     *
     * @param scmConfig SCM 仓库配置
     * @param ref 分支或提交号
     * @param fileFilter 文件路径过滤器
     * @return 临时工作区
     * @throws IOException 临时目录创建或文件写入失败时抛出
     */
    public ScmCodeIndexWorkspace readRepositoryFiles(ScmConfig scmConfig,
                                                     String ref,
                                                     Predicate<String> fileFilter) throws IOException {
        long startedAt = System.currentTimeMillis();
        Path repositoryRoot = Files.createTempDirectory("argus-code-index-");
        ScmCodeIndexWorkspace workspace = new ScmCodeIndexWorkspace(repositoryRoot);
        if (scmConfig == null || isBlank(scmConfig.getScmProvider())) {
            workspace.getWarnings().add("SCM 配置缺失或 provider 为空");
            return workspace;
        }
        ScmPlatformService platformService = scmPlatformServiceFactory.getRequired(scmConfig.getScmProvider());
        if (materializeRepositorySnapshot(platformService, scmConfig, ref, fileFilter, workspace, startedAt)) {
            return workspace;
        }
        Set<String> repositoryFiles = normalizeFilePaths(platformService.listRepositoryFiles(scmConfig, ref));
        Set<String> indexFiles = new LinkedHashSet<>();
        for (String filePath : repositoryFiles) {
            if (fileFilter == null || fileFilter.test(filePath)) {
                indexFiles.add(filePath);
            }
        }
        if (indexFiles.isEmpty()) {
            workspace.getWarnings().add("SCM 仓库文件树未发现可扫描文件");
            return workspace;
        }
        readFilesIntoWorkspace(platformService, scmConfig, ref, indexFiles, workspace);
        log.info("SCM 仓库文件树读取完成, provider={}, projectId={}, ref={}, repositoryFileCount={}, indexFileCount={}, loadedFileCount={}, costMs={}",
                scmConfig.getScmProvider(), scmConfig.getProjectId(), ref, repositoryFiles.size(), indexFiles.size(),
                workspace.getLoadedFilePaths().size(), System.currentTimeMillis() - startedAt);
        return workspace;
    }

    private boolean materializeRepositorySnapshot(ScmPlatformService platformService,
                                                  ScmConfig scmConfig,
                                                  String ref,
                                                  Predicate<String> fileFilter,
                                                  ScmCodeIndexWorkspace workspace,
                                                  long startedAt) {
        boolean materialized = platformService.materializeRepositoryFiles(
                scmConfig,
                ref,
                fileFilter,
                workspace.getRepositoryRoot(),
                workspace.getLoadedFilePaths(),
                workspace.getFailedFilePaths(),
                workspace.getWarnings());
        if (!materialized) {
            return false;
        }
        if (workspace.getLoadedFilePaths().isEmpty()) {
            workspace.getWarnings().add("SCM 仓库快照未发现可扫描文件");
        }
        log.info("SCM 仓库快照读取完成, provider={}, projectId={}, ref={}, loadedFileCount={}, failedFileCount={}, costMs={}",
                scmConfig.getScmProvider(), scmConfig.getProjectId(), ref, workspace.getLoadedFilePaths().size(),
                workspace.getFailedFilePaths().size(), System.currentTimeMillis() - startedAt);
        return true;
    }

    private Set<String> normalizeFilePaths(Collection<String> filePaths) {
        Set<String> normalizedFilePaths = new LinkedHashSet<>();
        if (filePaths == null) {
            return normalizedFilePaths;
        }
        for (String filePath : filePaths) {
            if (isBlank(filePath)) {
                continue;
            }
            normalizedFilePaths.add(filePath.trim().replace('\\', '/').replaceFirst("^/+", ""));
        }
        return normalizedFilePaths;
    }

    private void readFilesIntoWorkspace(ScmPlatformService platformService,
                                        ScmConfig scmConfig,
                                        String ref,
                                        Collection<String> filePaths,
                                        ScmCodeIndexWorkspace workspace) {
        int readCount = 0;
        for (String filePath : normalizeFilePaths(filePaths)) {
            readCount++;
            Path targetPath = resolveTargetPath(workspace.getRepositoryRoot(), filePath);
            if (targetPath == null) {
                workspace.getFailedFilePaths().add(filePath);
                workspace.getWarnings().add("非法文件路径: " + filePath);
                continue;
            }
            try {
                String content = platformService.getFileContent(scmConfig, filePath, ref);
                if (content == null) {
                    workspace.getFailedFilePaths().add(filePath);
                    workspace.getWarnings().add("SCM 文件内容为空: " + filePath);
                    continue;
                }
                Files.createDirectories(targetPath.getParent());
                Files.writeString(targetPath, content, StandardCharsets.UTF_8);
                workspace.getLoadedFilePaths().add(filePath);
            } catch (RuntimeException | IOException e) {
                workspace.getFailedFilePaths().add(filePath);
                workspace.getWarnings().add("SCM 文件读取失败: " + filePath + " - " + e.getMessage());
            }
            if (readCount % 100 == 0) {
                log.info("SCM 文件读取进度, provider={}, projectId={}, ref={}, readCount={}, loadedFileCount={}, failedFileCount={}",
                        scmConfig.getScmProvider(), scmConfig.getProjectId(), ref, readCount,
                        workspace.getLoadedFilePaths().size(), workspace.getFailedFilePaths().size());
            }
        }
    }

    private Path resolveTargetPath(Path repositoryRoot, String filePath) {
        Path targetPath = repositoryRoot.resolve(filePath).normalize();
        return targetPath.startsWith(repositoryRoot) ? targetPath : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
