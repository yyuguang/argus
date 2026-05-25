package com.lnzz.argus.codeindex.service.impl;

import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.req.CodeClassPageReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeClassIndexResDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.scanner.CodeIndexBuilder;
import com.lnzz.argus.codeindex.scanner.JavaFileIndex;
import com.lnzz.argus.codeindex.scanner.MavenModuleScanner;
import com.lnzz.argus.codeindex.scanner.ModuleScanResult;
import com.lnzz.argus.codeindex.scanner.RepositoryCodeIndexDraft;
import com.lnzz.argus.codeindex.service.CodeIndexScanService;
import com.lnzz.argus.codeindex.service.CodeIndexService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.codeindex.support.ScmCodeIndexFileReader;
import com.lnzz.argus.codeindex.support.ScmCodeIndexWorkspace;
import com.lnzz.argus.common.request.BasePageRequest;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @classname: CodeIndexScanServiceImpl
 * @author: Fantasy
 * @date: 2026/05/19 17:40
 * @description: 源码索引扫描编排服务实现，首期支持基于 SCM 已知文件路径的定向扫描。
 */
@Slf4j
@Service
public class CodeIndexScanServiceImpl implements CodeIndexScanService {

    private final CodeIndexService codeIndexService;
    private final ScmCodeIndexFileReader scmCodeIndexFileReader;
    private final MavenModuleScanner mavenModuleScanner;
    private final CodeIndexBuilder codeIndexBuilder;

    public CodeIndexScanServiceImpl(CodeIndexService codeIndexService, ScmCodeIndexFileReader scmCodeIndexFileReader) {
        this.codeIndexService = codeIndexService;
        this.scmCodeIndexFileReader = scmCodeIndexFileReader;
        this.mavenModuleScanner = new MavenModuleScanner();
        this.codeIndexBuilder = new CodeIndexBuilder();
    }

    @Override
    public CodeIndexSummaryResDTO scanFull(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO) {
        CodeIndexScanReqDTO effectiveRequest = copyRequest(requestDTO);
        if (Boolean.TRUE.equals(effectiveRequest.getForceRebuild())) {
            effectiveRequest.setScanType(CodeIndexConstants.ScanType.REBUILD);
        } else if (!hasText(effectiveRequest.getScanType())) {
            effectiveRequest.setScanType(CodeIndexConstants.ScanType.FULL);
        }
        return scanKnownFiles(scmConfig, effectiveRequest);
    }

    @Override
    public CodeIndexSummaryResDTO scanIncremental(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO, List<DiffFile> diffFiles) {
        long scanStartedAt = System.currentTimeMillis();
        CodeIndexScanReqDTO effectiveRequest = adaptDiffRequest(requestDTO, diffFiles);
        if (!hasText(effectiveRequest.getScanType())) {
            effectiveRequest.setScanType(CodeIndexConstants.ScanType.INCREMENTAL);
        }
        log.info("源码索引增量扫描开始, scmConfigId={}, provider={}, projectId={}, repoName={}, ref={}, scanType={}, filePathCount={}, deletedFilePathCount={}, diffFileCount={}",
                configId(scmConfig), provider(scmConfig), projectId(scmConfig), repoName(scmConfig), resolveRef(effectiveRequest),
                effectiveRequest.getScanType(), safeList(effectiveRequest.getFilePaths()).size(),
                safeList(effectiveRequest.getDeletedFilePaths()).size(), diffFiles == null ? 0 : diffFiles.size());
        try {
            RepositoryCodeIndexDraft changedDraft = effectiveRequest.getFilePaths().isEmpty()
                    ? new RepositoryCodeIndexDraft()
                    : buildDraft(scmConfig, effectiveRequest);
            RepositoryCodeIndexDraft mergedDraft = mergeWithLatestSuccessfulIndex(scmConfig, effectiveRequest, changedDraft);
            CodeIndexSummaryResDTO response = codeIndexService.saveSuccessfulIndex(scmConfig, effectiveRequest, mergedDraft);
            log.info("源码索引增量扫描完成, scmConfigId={}, provider={}, projectId={}, repoName={}, ref={}, scanType={}, moduleCount={}, sourceRootCount={}, javaFileCount={}, classCount={}, packageCount={}, warningCount={}, costMs={}",
                    configId(scmConfig), provider(scmConfig), projectId(scmConfig), repoName(scmConfig), resolveRef(effectiveRequest),
                    effectiveRequest.getScanType(), mergedDraft.getModuleCount(), mergedDraft.getSourceRootCount(),
                    mergedDraft.getJavaFileCount(), mergedDraft.getClassCount(), mergedDraft.getPackageCount(),
                    mergedDraft.getWarningCount(), System.currentTimeMillis() - scanStartedAt);
            return response;
        } catch (IOException | RuntimeException e) {
            log.warn("源码索引增量扫描失败, scmConfigId={}, provider={}, projectId={}, repoName={}, ref={}, scanType={}, filePathCount={}, deletedFilePathCount={}, costMs={}, error={}",
                    configId(scmConfig), provider(scmConfig), projectId(scmConfig), repoName(scmConfig), resolveRef(effectiveRequest),
                    effectiveRequest.getScanType(), safeList(effectiveRequest.getFilePaths()).size(),
                    safeList(effectiveRequest.getDeletedFilePaths()).size(), System.currentTimeMillis() - scanStartedAt,
                    e.getMessage(), e);
            return codeIndexService.markScanFailed(scmConfig, effectiveRequest, e.getMessage());
        }
    }

    @Override
    public CodeIndexSummaryResDTO scanKnownFiles(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO) {
        long scanStartedAt = System.currentTimeMillis();
        CodeIndexScanReqDTO effectiveRequest = requestDTO == null ? new CodeIndexScanReqDTO() : requestDTO;
        log.info("源码索引扫描开始, scmConfigId={}, provider={}, projectId={}, repoName={}, ref={}, scanType={}, filePathCount={}, sourceRootOverrideCount={}",
                configId(scmConfig), provider(scmConfig), projectId(scmConfig), repoName(scmConfig), resolveRef(effectiveRequest),
                effectiveRequest.getScanType(), safeList(effectiveRequest.getFilePaths()).size(),
                safeList(effectiveRequest.getSourceRootOverrides()).size());
        try {
            RepositoryCodeIndexDraft draft = buildDraft(scmConfig, effectiveRequest);
            CodeIndexSummaryResDTO response = codeIndexService.saveSuccessfulIndex(scmConfig, effectiveRequest, draft);
            log.info("源码索引扫描完成, scmConfigId={}, provider={}, projectId={}, repoName={}, ref={}, scanType={}, moduleCount={}, sourceRootCount={}, javaFileCount={}, classCount={}, packageCount={}, warningCount={}, costMs={}",
                    configId(scmConfig), provider(scmConfig), projectId(scmConfig), repoName(scmConfig), resolveRef(effectiveRequest),
                    effectiveRequest.getScanType(), draft.getModuleCount(), draft.getSourceRootCount(), draft.getJavaFileCount(),
                    draft.getClassCount(), draft.getPackageCount(), draft.getWarningCount(),
                    System.currentTimeMillis() - scanStartedAt);
            return response;
        } catch (IOException | RuntimeException e) {
            log.warn("源码索引扫描失败, scmConfigId={}, provider={}, projectId={}, repoName={}, ref={}, scanType={}, filePathCount={}, costMs={}, error={}",
                    configId(scmConfig), provider(scmConfig), projectId(scmConfig), repoName(scmConfig), resolveRef(effectiveRequest),
                    effectiveRequest.getScanType(), safeList(effectiveRequest.getFilePaths()).size(),
                    System.currentTimeMillis() - scanStartedAt, e.getMessage(), e);
            return codeIndexService.markScanFailed(scmConfig, effectiveRequest, e.getMessage());
        }
    }

    @Override
    public CodeIndexSummaryResDTO scanDiffFiles(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO, List<DiffFile> diffFiles) {
        return scanKnownFiles(scmConfig, adaptDiffRequest(requestDTO, diffFiles));
    }

    private RepositoryCodeIndexDraft buildDraft(ScmConfig scmConfig, CodeIndexScanReqDTO effectiveRequest) throws IOException {
        long draftStartedAt = System.currentTimeMillis();
        String ref = resolveRef(effectiveRequest);
        log.info("源码索引草稿准备开始, scmConfigId={}, provider={}, projectId={}, repoName={}, ref={}, scanType={}, requestedFilePathCount={}",
                configId(scmConfig), provider(scmConfig), projectId(scmConfig), repoName(scmConfig), ref,
                effectiveRequest.getScanType(), safeList(effectiveRequest.getFilePaths()).size());
        try (ScmCodeIndexWorkspace workspace = safeList(effectiveRequest.getFilePaths()).isEmpty()
                ? scmCodeIndexFileReader.readRepositoryFiles(scmConfig, ref, this::shouldReadForIndex)
                : scmCodeIndexFileReader.readKnownFiles(scmConfig, ref, effectiveRequest.getFilePaths())) {
            long fileReadFinishedAt = System.currentTimeMillis();
            log.info("源码索引 SCM 文件读取完成, scmConfigId={}, provider={}, projectId={}, repoName={}, ref={}, loadedFileCount={}, failedFileCount={}, warningCount={}, costMs={}",
                    configId(scmConfig), provider(scmConfig), projectId(scmConfig), repoName(scmConfig), ref,
                    workspace.getLoadedFilePaths().size(), workspace.getFailedFilePaths().size(),
                    workspace.getWarnings().size(), fileReadFinishedAt - draftStartedAt);
            if (workspace.getLoadedFilePaths().isEmpty()) {
                throw new IllegalStateException("未读取到可扫描文件");
            }
            long moduleScanStartedAt = System.currentTimeMillis();
            List<ModuleScanResult> modules = mavenModuleScanner.scan(workspace.getRepositoryRoot());
            log.info("源码索引 Maven 模块扫描完成, scmConfigId={}, provider={}, projectId={}, repoName={}, ref={}, moduleCount={}, costMs={}",
                    configId(scmConfig), provider(scmConfig), projectId(scmConfig), repoName(scmConfig), ref,
                    modules.size(), System.currentTimeMillis() - moduleScanStartedAt);
            if (modules.isEmpty()) {
                throw new IllegalStateException("未识别到 Maven 模块，当前扫描适配需要 pom.xml 文件");
            }
            long buildStartedAt = System.currentTimeMillis();
            RepositoryCodeIndexDraft draft = codeIndexBuilder.build(
                    workspace.getRepositoryRoot(), modules, effectiveRequest.getSourceRootOverrides());
            mergeWorkspaceWarnings(draft, workspace);
            log.info("源码索引草稿准备完成, scmConfigId={}, provider={}, projectId={}, repoName={}, ref={}, scanType={}, loadedFileCount={}, moduleCount={}, sourceRootCount={}, javaFileCount={}, classCount={}, packageCount={}, warningCount={}, buildCostMs={}, totalCostMs={}",
                    configId(scmConfig), provider(scmConfig), projectId(scmConfig), repoName(scmConfig), ref,
                    effectiveRequest.getScanType(), workspace.getLoadedFilePaths().size(), draft.getModuleCount(),
                    draft.getSourceRootCount(), draft.getJavaFileCount(), draft.getClassCount(), draft.getPackageCount(),
                    draft.getWarningCount(), System.currentTimeMillis() - buildStartedAt,
                    System.currentTimeMillis() - draftStartedAt);
            return draft;
        }
    }

    private CodeIndexScanReqDTO adaptDiffRequest(CodeIndexScanReqDTO requestDTO, List<DiffFile> diffFiles) {
        CodeIndexScanReqDTO effectiveRequest = copyRequest(requestDTO);
        Set<String> filePaths = new LinkedHashSet<>(safeList(effectiveRequest.getFilePaths()));
        List<String> deletedFilePaths = new ArrayList<>(safeList(effectiveRequest.getDeletedFilePaths()));
        if (diffFiles != null) {
            for (DiffFile diffFile : diffFiles) {
                if (diffFile == null) {
                    continue;
                }
                String effectivePath = diffFile.getEffectivePath();
                if (diffFile.isDeletedFile()) {
                    if (hasText(effectivePath)) {
                        deletedFilePaths.add(effectivePath);
                    }
                    continue;
                }
                if (hasText(effectivePath) && shouldReadForIndex(effectivePath)) {
                    filePaths.add(effectivePath);
                }
                if (hasText(effectivePath) && effectivePath.endsWith("pom.xml")) {
                    effectiveRequest.setScanType(CodeIndexConstants.ScanType.MODULE_RESCAN);
                }
            }
        }
        if (!filePaths.isEmpty()) {
            filePaths.add("pom.xml");
        }
        effectiveRequest.setFilePaths(new ArrayList<>(filePaths));
        effectiveRequest.setDeletedFilePaths(deletedFilePaths);
        if (!hasText(effectiveRequest.getScanType())) {
            effectiveRequest.setScanType(CodeIndexConstants.ScanType.INCREMENTAL);
        }
        return effectiveRequest;
    }

    private RepositoryCodeIndexDraft mergeWithLatestSuccessfulIndex(ScmConfig scmConfig,
                                                                    CodeIndexScanReqDTO requestDTO,
                                                                    RepositoryCodeIndexDraft changedDraft) {
        if (scmConfig == null) {
            return changedDraft;
        }
        CodeIndexSummaryResDTO latestIndex = codeIndexService.getLatestSuccessfulIndex(
                scmConfig.getId(), firstText(requestDTO.getBranchName(), CodeIndexConstants.DEFAULT_BRANCH, CodeIndexConstants.DEFAULT_BRANCH));
        if (latestIndex == null || latestIndex.getIndexId() == null) {
            return changedDraft;
        }
        List<JavaFileIndex> mergedClasses = new ArrayList<>();
        Set<String> changedFilePaths = changedFilePaths(changedDraft, requestDTO);
        for (CodeClassIndexResDTO classIndex : listAllClasses(latestIndex.getIndexId())) {
            if (!changedFilePaths.contains(classIndex.getFilePath())) {
                mergedClasses.add(toJavaFileIndex(classIndex));
            }
        }
        mergedClasses.addAll(changedDraft.getClasses());
        RepositoryCodeIndexDraft mergedDraft = codeIndexBuilder.build(changedDraft.getModules(), mergedClasses);
        mergedDraft.getWarnings().addAll(changedDraft.getWarnings());
        mergedDraft.setWarningCount(mergedDraft.getWarnings().size());
        return mergedDraft;
    }

    private Set<String> changedFilePaths(RepositoryCodeIndexDraft changedDraft, CodeIndexScanReqDTO requestDTO) {
        Set<String> changedFilePaths = new HashSet<>(safeList(requestDTO.getDeletedFilePaths()));
        safeList(requestDTO.getFilePaths()).stream()
                .filter(this::hasText)
                .filter(this::shouldReadForIndex)
                .filter(filePath -> filePath.endsWith(".java"))
                .forEach(changedFilePaths::add);
        changedDraft.getClasses().stream()
                .map(JavaFileIndex::getFilePath)
                .filter(this::hasText)
                .forEach(changedFilePaths::add);
        return changedFilePaths;
    }

    private List<CodeClassIndexResDTO> listAllClasses(Long indexId) {
        List<CodeClassIndexResDTO> records = new ArrayList<>();
        int pageNo = BasePageRequest.DEFAULT_PAGE_NO;
        while (true) {
            CodeClassPageReqDTO requestDTO = new CodeClassPageReqDTO();
            requestDTO.setPageNo(pageNo);
            requestDTO.setPageSize(BasePageRequest.MAX_PAGE_SIZE);
            PageResult<CodeClassIndexResDTO> page = codeIndexService.pageClasses(indexId, requestDTO);
            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                break;
            }
            records.addAll(page.getRecords());
            if (records.size() >= page.getTotal()) {
                break;
            }
            pageNo++;
        }
        return records;
    }

    private JavaFileIndex toJavaFileIndex(CodeClassIndexResDTO source) {
        JavaFileIndex target = new JavaFileIndex();
        target.setModulePath(source.getModulePath());
        target.setSourceRoot(source.getSourceRoot());
        target.setFilePath(source.getFilePath());
        target.setFileSha(source.getFileSha());
        target.setPackageName(source.getPackageName());
        target.setClassName(source.getClassName());
        target.setQualifiedName(source.getQualifiedName());
        target.setClassKind(source.getClassKind());
        target.setPrimaryType(source.getPrimaryType());
        target.setLineStart(source.getLineStart());
        target.setLineEnd(source.getLineEnd());
        target.setParserStatus(source.getParserStatus());
        return target;
    }

    private void mergeWorkspaceWarnings(RepositoryCodeIndexDraft draft, ScmCodeIndexWorkspace workspace) {
        if (workspace.getWarnings().isEmpty()) {
            return;
        }
        draft.getWarnings().addAll(workspace.getWarnings());
        draft.setWarningCount(draft.getWarnings().size());
    }

    private CodeIndexScanReqDTO copyRequest(CodeIndexScanReqDTO source) {
        CodeIndexScanReqDTO target = new CodeIndexScanReqDTO();
        if (source == null) {
            return target;
        }
        target.setBranchName(source.getBranchName());
        target.setCommitSha(source.getCommitSha());
        target.setBaseCommitSha(source.getBaseCommitSha());
        target.setScanType(source.getScanType());
        target.setForceRebuild(source.getForceRebuild());
        target.setReason(source.getReason());
        target.setFilePaths(new ArrayList<>(safeList(source.getFilePaths())));
        target.setDeletedFilePaths(new ArrayList<>(safeList(source.getDeletedFilePaths())));
        target.setSourceRootOverrides(new ArrayList<>(safeList(source.getSourceRootOverrides())));
        return target;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private boolean shouldReadForIndex(String filePath) {
        return filePath.endsWith(".java") || filePath.endsWith("pom.xml");
    }

    private String firstText(String first, String second, String fallback) {
        if (hasText(first)) {
            return first;
        }
        return hasText(second) ? second : fallback;
    }

    private String resolveRef(CodeIndexScanReqDTO requestDTO) {
        return firstText(requestDTO == null ? null : requestDTO.getCommitSha(),
                requestDTO == null ? null : requestDTO.getBranchName(),
                CodeIndexConstants.DEFAULT_BRANCH);
    }

    private Long configId(ScmConfig scmConfig) {
        return scmConfig == null ? null : scmConfig.getId();
    }

    private String provider(ScmConfig scmConfig) {
        return scmConfig == null ? null : scmConfig.getScmProvider();
    }

    private Long projectId(ScmConfig scmConfig) {
        return scmConfig == null ? null : scmConfig.getProjectId();
    }

    private String repoName(ScmConfig scmConfig) {
        return scmConfig == null ? null : scmConfig.getRepoName();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
