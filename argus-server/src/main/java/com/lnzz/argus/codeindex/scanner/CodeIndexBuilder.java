package com.lnzz.argus.codeindex.scanner;

import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @classname: CodeIndexBuilder
 * @author: Fantasy
 * @date: 2026/05/19 16:55
 * @description: 源码索引聚合器，整合模块、源码根和 Java 文件解析结果生成索引草稿。
 */
@Slf4j
public class CodeIndexBuilder {

    private static final int PARSE_PROGRESS_INTERVAL = 500;
    private static final long SLOW_PARSE_FILE_COST_MS = 1000L;

    private final SourceRootDiscoverer sourceRootDiscoverer;
    private final JavaSourceIndexParser javaSourceIndexParser;

    public CodeIndexBuilder() {
        this(new SourceRootDiscoverer(), new JavaSourceIndexParser());
    }

    public CodeIndexBuilder(SourceRootDiscoverer sourceRootDiscoverer, JavaSourceIndexParser javaSourceIndexParser) {
        this.sourceRootDiscoverer = sourceRootDiscoverer;
        this.javaSourceIndexParser = javaSourceIndexParser;
    }

    /**
     * 从仓库文件树生成源码索引草稿。
     *
     * @param repositoryRoot 仓库根目录
     * @param modules 模块扫描结果
     * @param sourceRootOverrides 源码根高级覆盖项
     * @return 源码索引草稿
     */
    public RepositoryCodeIndexDraft build(Path repositoryRoot, List<ModuleScanResult> modules,
                                          Collection<String> sourceRootOverrides) {
        if (repositoryRoot == null || modules == null) {
            return build(List.of(), List.of());
        }
        long buildStartedAt = System.currentTimeMillis();
        List<JavaFileIndex> classIndexes = new ArrayList<>();
        Path normalizedRoot = repositoryRoot.toAbsolutePath().normalize();
        log.info("源码索引草稿构建开始, repositoryRoot={}, moduleCount={}, sourceRootOverrideCount={}",
                normalizedRoot, modules.size(), sourceRootOverrides == null ? 0 : sourceRootOverrides.size());
        for (ModuleScanResult module : modules) {
            long moduleStartedAt = System.currentTimeMillis();
            List<String> sourceRoots = sourceRootDiscoverer.discover(normalizedRoot, module, sourceRootOverrides);
            module.setSourceRoots(new ArrayList<>(sourceRoots));
            int moduleJavaFileCount = 0;
            int moduleClassCount = 0;
            int moduleFailedFileCount = 0;
            int parsedFileCount = 0;
            log.info("源码索引模块扫描开始, modulePath={}, moduleName={}, sourceRootCount={}",
                    module.getModulePath(), module.getModuleName(), sourceRoots.size());
            for (String sourceRoot : sourceRoots) {
                long sourceRootStartedAt = System.currentTimeMillis();
                List<Path> javaFiles = listJavaFiles(normalizedRoot.resolve(sourceRoot));
                moduleJavaFileCount += javaFiles.size();
                log.info("源码索引源码根扫描开始, modulePath={}, sourceRoot={}, javaFileCount={}",
                        module.getModulePath(), sourceRoot, javaFiles.size());
                for (Path javaFile : javaFiles) {
                    long fileStartedAt = System.currentTimeMillis();
                    List<JavaFileIndex> parsedIndexes = javaSourceIndexParser.parse(normalizedRoot,
                            module.getModulePath(), sourceRoot, javaFile);
                    long fileCostMs = System.currentTimeMillis() - fileStartedAt;
                    classIndexes.addAll(parsedIndexes);
                    long successCount = parsedIndexes.stream()
                            .filter(this::successfulClassIndex)
                            .count();
                    moduleClassCount += (int) successCount;
                    if (successCount == 0) {
                        moduleFailedFileCount++;
                    }
                    parsedFileCount++;
                    String filePath = normalizeRelativePath(normalizedRoot, javaFile);
                    if (fileCostMs >= SLOW_PARSE_FILE_COST_MS) {
                        log.warn("源码索引 Java 文件解析耗时偏高, modulePath={}, sourceRoot={}, filePath={}, parserStatus={}, classCount={}, costMs={}",
                                module.getModulePath(), sourceRoot, filePath, resolveFileParserStatus(parsedIndexes),
                                successCount, fileCostMs);
                    }
                    // 大仓库只按固定间隔输出进度，既能观察是否卡住，又避免每个文件刷一行日志。
                    if (parsedFileCount % PARSE_PROGRESS_INTERVAL == 0) {
                        log.info("源码索引 Java 文件解析进度, modulePath={}, parsedFileCount={}, javaFileCount={}, classCount={}, failedFileCount={}",
                                module.getModulePath(), parsedFileCount, moduleJavaFileCount,
                                moduleClassCount, moduleFailedFileCount);
                    }
                }
                log.info("源码索引源码根扫描完成, modulePath={}, sourceRoot={}, javaFileCount={}, costMs={}",
                        module.getModulePath(), sourceRoot, javaFiles.size(),
                        System.currentTimeMillis() - sourceRootStartedAt);
            }
            module.setJavaFileCount(moduleJavaFileCount);
            module.setClassCount(moduleClassCount);
            module.setScanStatus(resolveModuleScanStatus(moduleJavaFileCount, moduleClassCount, moduleFailedFileCount));
            if (sourceRoots.isEmpty()) {
                module.getWarnings().add("未发现源码根");
            }
            log.info("源码索引模块扫描完成, modulePath={}, moduleName={}, sourceRootCount={}, javaFileCount={}, classCount={}, failedFileCount={}, scanStatus={}, costMs={}",
                    module.getModulePath(), module.getModuleName(), sourceRoots.size(), moduleJavaFileCount,
                    moduleClassCount, moduleFailedFileCount, module.getScanStatus(),
                    System.currentTimeMillis() - moduleStartedAt);
        }
        RepositoryCodeIndexDraft draft = build(modules, classIndexes);
        log.info("源码索引草稿构建完成, repositoryRoot={}, moduleCount={}, sourceRootCount={}, javaFileCount={}, classCount={}, packageCount={}, warningCount={}, costMs={}",
                normalizedRoot, draft.getModuleCount(), draft.getSourceRootCount(), draft.getJavaFileCount(),
                draft.getClassCount(), draft.getPackageCount(), draft.getWarningCount(),
                System.currentTimeMillis() - buildStartedAt);
        return draft;
    }

    /**
     * 聚合模块和 Java 类型解析结果。
     *
     * @param modules 模块扫描结果
     * @param classIndexes Java 类型索引
     * @return 源码索引草稿
     */
    public RepositoryCodeIndexDraft build(List<ModuleScanResult> modules, List<JavaFileIndex> classIndexes) {
        RepositoryCodeIndexDraft draft = new RepositoryCodeIndexDraft();
        List<ModuleScanResult> normalizedModules = modules == null ? List.of() : modules;
        List<JavaFileIndex> normalizedClassIndexes = classIndexes == null ? List.of() : classIndexes;
        draft.setModules(new ArrayList<>(normalizedModules));
        draft.setClasses(new ArrayList<>(normalizedClassIndexes));
        draft.setModuleCount(normalizedModules.size());
        draft.setSourceRootCount(countSourceRoots(normalizedModules));
        draft.setJavaFileCount(countJavaFiles(normalizedClassIndexes));

        Map<String, RepositoryCodeIndexDraft.PackageDraft> packageDraftMap = new LinkedHashMap<>();
        Set<String> warningSet = new LinkedHashSet<>();
        for (ModuleScanResult module : normalizedModules) {
            warningSet.addAll(module.getWarnings());
        }
        for (JavaFileIndex classIndex : normalizedClassIndexes) {
            if (!successfulClassIndex(classIndex)) {
                if (!isBlank(classIndex.getErrorMessage())) {
                    warningSet.add("Java 文件解析失败: " + classIndex.getFilePath() + " - " + classIndex.getErrorMessage());
                }
                continue;
            }
            draft.setClassCount(draft.getClassCount() + 1);
            String qualifiedName = classIndex.getQualifiedName();
            if (draft.getQualifiedNameToFilePath().containsKey(qualifiedName)) {
                warningSet.add("重复全限定类名: " + qualifiedName);
            } else {
                draft.getQualifiedNameToFilePath().put(qualifiedName, classIndex.getFilePath());
            }
            RepositoryCodeIndexDraft.PackageDraft packageDraft = packageDraftMap.computeIfAbsent(
                    defaultString(classIndex.getPackageName()),
                    packageName -> buildPackageDraft(packageName, classIndex.getModulePath())
            );
            if (!packageDraft.getModulePaths().contains(defaultString(classIndex.getModulePath()))) {
                packageDraft.getModulePaths().add(defaultString(classIndex.getModulePath()));
            }
            packageDraft.setClassCount(packageDraft.getClassCount() + 1);
        }
        for (RepositoryCodeIndexDraft.PackageDraft packageDraft : packageDraftMap.values()) {
            boolean ambiguous = packageDraft.getModulePaths().size() > 1;
            packageDraft.setAmbiguous(ambiguous);
            packageDraft.setConfidence(ambiguous ? CodeIndexConstants.Confidence.MEDIUM : CodeIndexConstants.Confidence.HIGH);
        }
        draft.setPackages(new ArrayList<>(packageDraftMap.values()));
        draft.setPackageCount(draft.getPackages().size());
        draft.setWarnings(new ArrayList<>(warningSet));
        draft.setWarningCount(draft.getWarnings().size());
        return draft;
    }

    private List<Path> listJavaFiles(Path sourceRoot) {
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        try (var stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().contains("/target/"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private RepositoryCodeIndexDraft.PackageDraft buildPackageDraft(String packageName, String modulePath) {
        RepositoryCodeIndexDraft.PackageDraft packageDraft = new RepositoryCodeIndexDraft.PackageDraft();
        packageDraft.setPackageName(defaultString(packageName));
        packageDraft.setPrimaryModulePath(defaultString(modulePath));
        packageDraft.getModulePaths().add(defaultString(modulePath));
        return packageDraft;
    }

    private String resolveModuleScanStatus(int javaFileCount, int classCount, int failedFileCount) {
        if (javaFileCount == 0) {
            return CodeIndexConstants.ScanStatus.PARTIAL;
        }
        if (classCount == 0 && failedFileCount > 0) {
            return CodeIndexConstants.ScanStatus.FAILED;
        }
        if (failedFileCount > 0) {
            return CodeIndexConstants.ScanStatus.PARTIAL;
        }
        return CodeIndexConstants.ScanStatus.SUCCESS;
    }

    private String resolveFileParserStatus(List<JavaFileIndex> parsedIndexes) {
        if (parsedIndexes == null || parsedIndexes.isEmpty()) {
            return CodeIndexConstants.ScanStatus.FAILED;
        }
        return parsedIndexes.stream().anyMatch(this::successfulClassIndex)
                ? CodeIndexConstants.ScanStatus.SUCCESS
                : CodeIndexConstants.ScanStatus.FAILED;
    }

    private int countSourceRoots(List<ModuleScanResult> modules) {
        return (int) modules.stream()
                .flatMap(module -> module.getSourceRoots().stream())
                .distinct()
                .count();
    }

    private int countJavaFiles(List<JavaFileIndex> classIndexes) {
        return (int) classIndexes.stream()
                .map(JavaFileIndex::getFilePath)
                .filter(filePath -> !isBlank(filePath))
                .distinct()
                .count();
    }

    private boolean successfulClassIndex(JavaFileIndex classIndex) {
        return classIndex != null
                && CodeIndexConstants.ScanStatus.SUCCESS.equals(classIndex.getParserStatus())
                && !isBlank(classIndex.getQualifiedName());
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String normalizeRelativePath(Path repositoryRoot, Path path) {
        if (repositoryRoot == null || path == null) {
            return "";
        }
        return repositoryRoot.toAbsolutePath().normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
