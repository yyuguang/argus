package com.lnzz.argus.codeindex.scanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @classname: SourceRootDiscoverer
 * @author: Fantasy
 * @date: 2026/05/19 16:55
 * @description: 源码根发现器，按模块路径识别标准源码根并合并高级覆盖项。
 */
public class SourceRootDiscoverer {

    private static final List<String> STANDARD_SOURCE_ROOTS = List.of(
            "src/main/java",
            "src/generated/java",
            "generated-sources"
    );

    /**
     * 发现模块源码根。
     *
     * @param repositoryRoot 仓库根目录
     * @param moduleResult 模块扫描结果
     * @param sourceRootOverrides 高级覆盖项
     * @return 仓库相对源码根路径列表
     */
    public List<String> discover(Path repositoryRoot, ModuleScanResult moduleResult, Collection<String> sourceRootOverrides) {
        if (repositoryRoot == null || moduleResult == null || !Files.isDirectory(repositoryRoot)) {
            return List.of();
        }
        Path normalizedRoot = repositoryRoot.toAbsolutePath().normalize();
        Path moduleRoot = resolveRepositoryPath(normalizedRoot, moduleResult.getModulePath());
        Set<String> sourceRoots = new LinkedHashSet<>();
        for (String standardSourceRoot : STANDARD_SOURCE_ROOTS) {
            Path candidate = moduleRoot.resolve(standardSourceRoot).normalize();
            if (Files.isDirectory(candidate) && !containsTargetPath(normalizedRoot, candidate)) {
                sourceRoots.add(normalizeRelativePath(normalizedRoot, candidate));
            }
        }
        if (sourceRootOverrides != null) {
            for (String override : sourceRootOverrides) {
                if (isBlank(override)) {
                    continue;
                }
                Path candidate = resolveRepositoryPath(normalizedRoot, override);
                if (Files.isDirectory(candidate)) {
                    sourceRoots.add(normalizeRelativePath(normalizedRoot, candidate));
                }
            }
        }
        return List.copyOf(sourceRoots);
    }

    /**
     * 发现模块标准源码根。
     *
     * @param repositoryRoot 仓库根目录
     * @param moduleResult 模块扫描结果
     * @return 仓库相对源码根路径列表
     */
    public List<String> discover(Path repositoryRoot, ModuleScanResult moduleResult) {
        return discover(repositoryRoot, moduleResult, List.of());
    }

    private Path resolveRepositoryPath(Path repositoryRoot, String relativePath) {
        if (isBlank(relativePath)) {
            return repositoryRoot;
        }
        return repositoryRoot.resolve(relativePath).normalize();
    }

    private String normalizeRelativePath(Path repositoryRoot, Path path) {
        return repositoryRoot.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private boolean containsTargetPath(Path repositoryRoot, Path path) {
        String normalized = normalizeRelativePath(repositoryRoot, path);
        return normalized.equals("target") || normalized.contains("/target/");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
