package com.lnzz.argus.codeindex.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.codeindex.dao.entity.CodeClassIndex;
import com.lnzz.argus.codeindex.dao.entity.CodeModuleIndex;
import com.lnzz.argus.codeindex.dao.entity.CodePackageIndex;
import com.lnzz.argus.codeindex.dao.entity.CodeRepositoryIndex;
import com.lnzz.argus.codeindex.dao.mapper.CodeClassIndexMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodeModuleIndexMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodePackageIndexMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodeRepositoryIndexMapper;
import com.lnzz.argus.codeindex.dto.req.CodeClassPageReqDTO;
import com.lnzz.argus.codeindex.dto.req.CodeIndexPageReqDTO;
import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeClassIndexResDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexDetailResDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.scanner.JavaFileIndex;
import com.lnzz.argus.codeindex.scanner.ModuleScanResult;
import com.lnzz.argus.codeindex.scanner.RepositoryCodeIndexDraft;
import com.lnzz.argus.codeindex.service.CodeIndexService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import com.lnzz.argus.common.constant.SystemDataConstants;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.scm.entity.ScmConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @classname: CodeIndexServiceImpl
 * @author: Fantasy
 * @date: 2026/05/19 17:15
 * @description: 源码索引服务实现，负责索引快照持久化、索引详情查询和 Java 类型索引分页。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeIndexServiceImpl implements CodeIndexService {

    private static final int DETAIL_INSERT_BATCH_SIZE = 500;

    private final CodeRepositoryIndexMapper repositoryIndexMapper;
    private final CodeModuleIndexMapper moduleIndexMapper;
    private final CodeClassIndexMapper classIndexMapper;
    private final CodePackageIndexMapper packageIndexMapper;

    @Override
    public PageResult<CodeIndexSummaryResDTO> pageIndexes(CodeIndexPageReqDTO requestDTO) {
        CodeIndexPageReqDTO effectiveRequest = requestDTO == null ? new CodeIndexPageReqDTO() : requestDTO;
        Page<CodeRepositoryIndex> page = repositoryIndexMapper.selectPage(
                new Page<>(effectiveRequest.normalizedPageNo(), effectiveRequest.normalizedPageSize()),
                buildRepositoryQuery(effectiveRequest));
        List<CodeIndexSummaryResDTO> records = page.getRecords().stream()
                .map(this::toSummary)
                .toList();
        return PageResult.of(records, page.getCurrent(), page.getSize(), page.getTotal());
    }

    @Override
    public CodeIndexDetailResDTO getIndexDetail(Long indexId) {
        CodeRepositoryIndex repositoryIndex = repositoryIndexMapper.selectById(indexId);
        if (repositoryIndex == null) {
            return null;
        }
        CodeIndexDetailResDTO detail = new CodeIndexDetailResDTO();
        detail.setIndexSummary(toSummary(repositoryIndex));
        detail.setModules(moduleIndexMapper.selectByIndexId(indexId).stream()
                .map(this::toModuleSummary)
                .toList());
        detail.setPackages(packageIndexMapper.selectList(new LambdaQueryWrapper<CodePackageIndex>()
                        .eq(CodePackageIndex::getIndexId, indexId)
                        .eq(CodePackageIndex::getIsDeleted, SystemDataConstants.NOT_DELETED))
                .stream()
                .map(this::toPackageSummary)
                .toList());
        return detail;
    }

    @Override
    public CodeIndexSummaryResDTO getLatestSuccessfulIndex(Long scmConfigId, String branchName) {
        return toSummary(repositoryIndexMapper.selectLatestSuccessful(scmConfigId, branchName));
    }

    @Override
    public CodeIndexSummaryResDTO getSuccessfulIndexByCommit(Long scmConfigId, String commitSha) {
        if (scmConfigId == null || !hasText(commitSha)) {
            return null;
        }
        CodeRepositoryIndex index = repositoryIndexMapper.selectByCommit(
                scmConfigId, commitSha, CodeIndexConstants.CURRENT_INDEX_VERSION);
        if (index == null || !CodeIndexConstants.ScanStatus.SUCCESS.equals(index.getScanStatus())) {
            return null;
        }
        return toSummary(index);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CodeIndexSummaryResDTO saveSuccessfulIndex(ScmConfig scmConfig,
                                                      CodeIndexScanReqDTO requestDTO,
                                                      RepositoryCodeIndexDraft draft) {
        if (scmConfig == null || requestDTO == null || draft == null) {
            return null;
        }
        long totalStartedAt = System.currentTimeMillis();
        String commitSha = resolveCommitSha(requestDTO);
        log.info("源码索引持久化开始, scmConfigId={}, provider={}, projectId={}, repoName={}, branchName={}, commitSha={}, scanType={}, moduleCount={}, classCount={}, packageCount={}, warningCount={}",
                scmConfig.getId(), scmConfig.getScmProvider(), scmConfig.getProjectId(), scmConfig.getRepoName(),
                requestDTO.getBranchName(), commitSha, requestDTO.getScanType(), draft.getModuleCount(),
                draft.getClassCount(), draft.getPackageCount(), draft.getWarningCount());
        long existingQueryStartedAt = System.currentTimeMillis();
        CodeRepositoryIndex existing = repositoryIndexMapper.selectByCommit(
                scmConfig.getId(), commitSha, CodeIndexConstants.CURRENT_INDEX_VERSION);
        log.info("源码索引持久化查询既有快照完成, scmConfigId={}, commitSha={}, existingIndexId={}, existingStatus={}, costMs={}",
                scmConfig.getId(), commitSha, existing == null ? null : existing.getId(),
                existing == null ? null : existing.getScanStatus(), System.currentTimeMillis() - existingQueryStartedAt);
        if (shouldReuseExistingSuccess(existing, requestDTO)) {
            log.info("源码索引持久化复用既有成功快照, scmConfigId={}, commitSha={}, indexId={}, costMs={}",
                    scmConfig.getId(), commitSha, existing.getId(), System.currentTimeMillis() - totalStartedAt);
            return toSummary(existing);
        }
        CodeRepositoryIndex repositoryIndex = buildRepositoryIndex(scmConfig, requestDTO, draft,
                CodeIndexConstants.ScanStatus.SUCCESS, null);
        long repositorySaveStartedAt = System.currentTimeMillis();
        if (existing == null) {
            repositoryIndexMapper.insert(repositoryIndex);
        } else {
            repositoryIndex.setId(existing.getId());
            repositoryIndex.setVersion(existing.getVersion());
            repositoryIndexMapper.updateById(repositoryIndex);
        }
        log.info("源码索引 repository 快照写入完成, scmConfigId={}, commitSha={}, indexId={}, operation={}, costMs={}",
                scmConfig.getId(), commitSha, repositoryIndex.getId(), existing == null ? "insert" : "update",
                System.currentTimeMillis() - repositorySaveStartedAt);
        if (existing != null) {
            long clearStartedAt = System.currentTimeMillis();
            clearIndexChildren(existing.getId());
            log.info("源码索引旧明细清理完成, scmConfigId={}, commitSha={}, indexId={}, costMs={}",
                    scmConfig.getId(), commitSha, existing.getId(), System.currentTimeMillis() - clearStartedAt);
        }
        Long indexId = repositoryIndex.getId();
        long moduleInsertStartedAt = System.currentTimeMillis();
        List<CodeModuleIndex> moduleEntities = new ArrayList<>();
        for (ModuleScanResult module : draft.getModules()) {
            moduleEntities.add(toModuleEntity(indexId, scmConfig.getId(), module));
        }
        int moduleInsertCount = insertModuleBatches(moduleEntities);
        log.info("源码索引 module 明细写入完成, scmConfigId={}, commitSha={}, indexId={}, insertCount={}, batchCount={}, batchSize={}, costMs={}",
                scmConfig.getId(), commitSha, indexId, moduleInsertCount, batchCount(moduleInsertCount),
                DETAIL_INSERT_BATCH_SIZE, System.currentTimeMillis() - moduleInsertStartedAt);
        long classInsertStartedAt = System.currentTimeMillis();
        List<CodeClassIndex> classEntities = new ArrayList<>();
        for (JavaFileIndex classIndex : draft.getClasses()) {
            if (hasText(classIndex.getQualifiedName())) {
                classEntities.add(toClassEntity(indexId, scmConfig.getId(), classIndex));
            }
        }
        int classInsertCount = insertClassBatches(classEntities);
        log.info("源码索引 class 明细写入完成, scmConfigId={}, commitSha={}, indexId={}, insertCount={}, skippedCount={}, batchCount={}, batchSize={}, costMs={}",
                scmConfig.getId(), commitSha, indexId, classInsertCount,
                Math.max(0, draft.getClasses().size() - classInsertCount), batchCount(classInsertCount),
                DETAIL_INSERT_BATCH_SIZE, System.currentTimeMillis() - classInsertStartedAt);
        long packageInsertStartedAt = System.currentTimeMillis();
        List<CodePackageIndex> packageEntities = new ArrayList<>();
        for (RepositoryCodeIndexDraft.PackageDraft packageDraft : draft.getPackages()) {
            packageEntities.add(toPackageEntity(indexId, scmConfig.getId(), packageDraft));
        }
        int packageInsertCount = insertPackageBatches(packageEntities);
        log.info("源码索引 package 明细写入完成, scmConfigId={}, commitSha={}, indexId={}, insertCount={}, batchCount={}, batchSize={}, costMs={}",
                scmConfig.getId(), commitSha, indexId, packageInsertCount, batchCount(packageInsertCount),
                DETAIL_INSERT_BATCH_SIZE, System.currentTimeMillis() - packageInsertStartedAt);
        log.info("源码索引持久化完成, scmConfigId={}, provider={}, projectId={}, repoName={}, branchName={}, commitSha={}, indexId={}, moduleInsertCount={}, classInsertCount={}, packageInsertCount={}, totalCostMs={}",
                scmConfig.getId(), scmConfig.getScmProvider(), scmConfig.getProjectId(), scmConfig.getRepoName(),
                requestDTO.getBranchName(), commitSha, indexId, moduleInsertCount, classInsertCount,
                packageInsertCount, System.currentTimeMillis() - totalStartedAt);
        return toSummary(repositoryIndex);
    }

    private boolean shouldReuseExistingSuccess(CodeRepositoryIndex existing, CodeIndexScanReqDTO requestDTO) {
        if (existing == null || !CodeIndexConstants.ScanStatus.SUCCESS.equals(existing.getScanStatus())) {
            return false;
        }
        return !Boolean.TRUE.equals(requestDTO.getForceRebuild())
                && !CodeIndexConstants.ScanType.REBUILD.equals(requestDTO.getScanType());
    }

    private void clearIndexChildren(Long indexId) {
        moduleIndexMapper.deletePhysicalByIndexId(indexId);
        classIndexMapper.deletePhysicalByIndexId(indexId);
        packageIndexMapper.deletePhysicalByIndexId(indexId);
    }

    private int insertModuleBatches(List<CodeModuleIndex> records) {
        int insertCount = 0;
        for (List<CodeModuleIndex> batch : batches(records)) {
            moduleIndexMapper.insertBatch(batch);
            insertCount += batch.size();
        }
        return insertCount;
    }

    private int insertClassBatches(List<CodeClassIndex> records) {
        int insertCount = 0;
        for (List<CodeClassIndex> batch : batches(records)) {
            classIndexMapper.insertBatch(batch);
            insertCount += batch.size();
        }
        return insertCount;
    }

    private int insertPackageBatches(List<CodePackageIndex> records) {
        int insertCount = 0;
        for (List<CodePackageIndex> batch : batches(records)) {
            packageIndexMapper.insertBatch(batch);
            insertCount += batch.size();
        }
        return insertCount;
    }

    private <T> List<List<T>> batches(List<T> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<List<T>> batches = new ArrayList<>();
        for (int start = 0; start < records.size(); start += DETAIL_INSERT_BATCH_SIZE) {
            int end = Math.min(start + DETAIL_INSERT_BATCH_SIZE, records.size());
            batches.add(records.subList(start, end));
        }
        return batches;
    }

    private int batchCount(int insertCount) {
        if (insertCount <= 0) {
            return 0;
        }
        return (insertCount + DETAIL_INSERT_BATCH_SIZE - 1) / DETAIL_INSERT_BATCH_SIZE;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CodeIndexSummaryResDTO markScanFailed(ScmConfig scmConfig,
                                                 CodeIndexScanReqDTO requestDTO,
                                                 String errorMessage) {
        if (scmConfig == null || requestDTO == null) {
            return null;
        }
        long totalStartedAt = System.currentTimeMillis();
        String commitSha = resolveCommitSha(requestDTO);
        log.warn("源码索引失败快照处理开始, scmConfigId={}, provider={}, projectId={}, repoName={}, branchName={}, commitSha={}, scanType={}, error={}",
                scmConfig.getId(), scmConfig.getScmProvider(), scmConfig.getProjectId(), scmConfig.getRepoName(),
                requestDTO.getBranchName(), commitSha, requestDTO.getScanType(), errorMessage);
        CodeRepositoryIndex existing = repositoryIndexMapper.selectByCommit(
                scmConfig.getId(), commitSha, CodeIndexConstants.CURRENT_INDEX_VERSION);
        if (existing != null && CodeIndexConstants.ScanStatus.SUCCESS.equals(existing.getScanStatus())) {
            log.warn("源码索引失败不覆盖既有成功快照, scmConfigId={}, commitSha={}, existingIndexId={}, costMs={}",
                    scmConfig.getId(), commitSha, existing.getId(), System.currentTimeMillis() - totalStartedAt);
            return buildFailedScanSummary(scmConfig, requestDTO, commitSha, errorMessage);
        }
        CodeRepositoryIndex repositoryIndex = buildRepositoryIndex(scmConfig, requestDTO,
                new RepositoryCodeIndexDraft(), CodeIndexConstants.ScanStatus.FAILED, errorMessage);
        if (existing == null) {
            repositoryIndexMapper.insert(repositoryIndex);
        } else {
            repositoryIndex.setId(existing.getId());
            repositoryIndexMapper.updateById(repositoryIndex);
        }
        log.warn("源码索引失败快照写入完成, scmConfigId={}, commitSha={}, indexId={}, operation={}, costMs={}",
                scmConfig.getId(), commitSha, repositoryIndex.getId(), existing == null ? "insert" : "update",
                System.currentTimeMillis() - totalStartedAt);
        return toSummary(repositoryIndex);
    }

    @Override
    public PageResult<CodeClassIndexResDTO> pageClasses(Long indexId, CodeClassPageReqDTO requestDTO) {
        CodeClassPageReqDTO effectiveRequest = requestDTO == null ? new CodeClassPageReqDTO() : requestDTO;
        Page<CodeClassIndex> page = classIndexMapper.selectPage(
                new Page<>(effectiveRequest.normalizedPageNo(), effectiveRequest.normalizedPageSize()),
                buildClassQuery(indexId, effectiveRequest));
        List<CodeClassIndexResDTO> records = page.getRecords().stream()
                .map(this::toClassRes)
                .toList();
        return PageResult.of(records, page.getCurrent(), page.getSize(), page.getTotal());
    }

    private LambdaQueryWrapper<CodeRepositoryIndex> buildRepositoryQuery(CodeIndexPageReqDTO requestDTO) {
        LambdaQueryWrapper<CodeRepositoryIndex> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CodeRepositoryIndex::getIsDeleted, SystemDataConstants.NOT_DELETED);
        wrapper.eq(requestDTO.getScmConfigId() != null, CodeRepositoryIndex::getScmConfigId, requestDTO.getScmConfigId());
        wrapper.eq(hasText(requestDTO.getBranchName()), CodeRepositoryIndex::getBranchName, requestDTO.getBranchName());
        wrapper.eq(hasText(requestDTO.getScanStatus()), CodeRepositoryIndex::getScanStatus, requestDTO.getScanStatus());
        wrapper.eq(requestDTO.getStale() != null, CodeRepositoryIndex::getStale, requestDTO.getStale());
        if (hasText(requestDTO.getKeyword())) {
            wrapper.and(keyword -> keyword
                    .like(CodeRepositoryIndex::getRepoName, requestDTO.getKeyword())
                    .or()
                    .like(CodeRepositoryIndex::getBranchName, requestDTO.getKeyword())
                    .or()
                    .like(CodeRepositoryIndex::getCommitSha, requestDTO.getKeyword())
                    .or()
                    .like(CodeRepositoryIndex::getLatestErrorMessage, requestDTO.getKeyword()));
        }
        wrapper.orderByDesc(CodeRepositoryIndex::getFinishedAt)
                .orderByDesc(CodeRepositoryIndex::getId);
        return wrapper;
    }

    private LambdaQueryWrapper<CodeClassIndex> buildClassQuery(Long indexId, CodeClassPageReqDTO requestDTO) {
        LambdaQueryWrapper<CodeClassIndex> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CodeClassIndex::getIsDeleted, SystemDataConstants.NOT_DELETED);
        wrapper.eq(indexId != null, CodeClassIndex::getIndexId, indexId);
        wrapper.eq(hasText(requestDTO.getModulePath()), CodeClassIndex::getModulePath, requestDTO.getModulePath());
        wrapper.eq(hasText(requestDTO.getPackageName()), CodeClassIndex::getPackageName, requestDTO.getPackageName());
        wrapper.eq(hasText(requestDTO.getClassName()), CodeClassIndex::getClassName, requestDTO.getClassName());
        wrapper.eq(hasText(requestDTO.getQualifiedName()), CodeClassIndex::getQualifiedName, requestDTO.getQualifiedName());
        wrapper.eq(hasText(requestDTO.getFilePath()), CodeClassIndex::getFilePath, requestDTO.getFilePath());
        wrapper.eq(hasText(requestDTO.getClassKind()), CodeClassIndex::getClassKind, requestDTO.getClassKind());
        wrapper.eq(hasText(requestDTO.getParserStatus()), CodeClassIndex::getParserStatus, requestDTO.getParserStatus());
        wrapper.eq(hasText(requestDTO.getConfidence()), CodeClassIndex::getConfidence, requestDTO.getConfidence());
        wrapper.orderByAsc(CodeClassIndex::getPackageName)
                .orderByAsc(CodeClassIndex::getClassName)
                .orderByAsc(CodeClassIndex::getFilePath);
        return wrapper;
    }

    private CodeRepositoryIndex buildRepositoryIndex(ScmConfig scmConfig,
                                                     CodeIndexScanReqDTO requestDTO,
                                                     RepositoryCodeIndexDraft draft,
                                                     String scanStatus,
                                                     String errorMessage) {
        LocalDateTime now = LocalDateTime.now();
        CodeRepositoryIndex entity = new CodeRepositoryIndex();
        entity.setScmConfigId(scmConfig.getId());
        entity.setScmProvider(scmConfig.getScmProvider());
        entity.setScmProjectId(scmConfig.getProjectId() == null ? null : String.valueOf(scmConfig.getProjectId()));
        entity.setRepoOwner(scmConfig.getRepoOwner());
        entity.setRepoName(scmConfig.getRepoName());
        entity.setBranchName(defaultIfBlank(requestDTO.getBranchName(), CodeIndexConstants.DEFAULT_BRANCH));
        entity.setCommitSha(resolveCommitSha(requestDTO));
        entity.setIndexVersion(CodeIndexConstants.CURRENT_INDEX_VERSION);
        entity.setScanType(defaultIfBlank(requestDTO.getScanType(), CodeIndexConstants.ScanType.FULL));
        entity.setTriggerType(CodeIndexConstants.TriggerType.MANUAL);
        entity.setScanStatus(scanStatus);
        entity.setModuleCount(defaultInt(draft.getModuleCount()));
        entity.setSourceRootCount(defaultInt(draft.getSourceRootCount()));
        entity.setJavaFileCount(defaultInt(draft.getJavaFileCount()));
        entity.setClassCount(defaultInt(draft.getClassCount()));
        entity.setPackageCount(defaultInt(draft.getPackageCount()));
        entity.setAmbiguousPackageCount(countAmbiguousPackages(draft));
        entity.setWarningCount(defaultInt(draft.getWarningCount()));
        entity.setConfidence(resolveConfidence(draft));
        entity.setStale(false);
        entity.setLatestErrorMessage(errorMessage);
        entity.setStartedAt(now);
        entity.setFinishedAt(now);
        entity.setIsDeleted(false);
        entity.setVersion(0);
        return entity;
    }

    private CodeModuleIndex toModuleEntity(Long indexId, Long scmConfigId, ModuleScanResult module) {
        CodeModuleIndex entity = new CodeModuleIndex();
        entity.setIndexId(indexId);
        entity.setScmConfigId(scmConfigId);
        entity.setModuleName(module.getModuleName());
        entity.setModulePath(module.getModulePath());
        entity.setParentModulePath(module.getParentModulePath());
        entity.setBuildType(module.getBuildType());
        entity.setPackaging(module.getPackaging());
        entity.setSourceRoots(JSON.toJSONString(module.getSourceRoots()));
        entity.setJavaFileCount(defaultInt(module.getJavaFileCount()));
        entity.setClassCount(defaultInt(module.getClassCount()));
        entity.setScanStatus(defaultIfBlank(module.getScanStatus(), CodeIndexConstants.ScanStatus.SUCCESS));
        entity.setWarningMessage(module.getWarnings().isEmpty() ? null : String.join("\n", module.getWarnings()));
        entity.setIsDeleted(false);
        entity.setVersion(0);
        return entity;
    }

    private CodeClassIndex toClassEntity(Long indexId, Long scmConfigId, JavaFileIndex classIndex) {
        CodeClassIndex entity = new CodeClassIndex();
        entity.setIndexId(indexId);
        entity.setScmConfigId(scmConfigId);
        entity.setModulePath(classIndex.getModulePath());
        entity.setSourceRoot(classIndex.getSourceRoot());
        entity.setFilePath(classIndex.getFilePath());
        entity.setFileSha(classIndex.getFileSha());
        entity.setPackageName(classIndex.getPackageName());
        entity.setClassName(classIndex.getClassName());
        entity.setQualifiedName(classIndex.getQualifiedName());
        entity.setClassKind(classIndex.getClassKind());
        entity.setPrimaryType(classIndex.getPrimaryType());
        entity.setLineStart(classIndex.getLineStart());
        entity.setLineEnd(classIndex.getLineEnd());
        entity.setImportsJson(JSON.toJSONString(classIndex.getImports()));
        entity.setParserStatus(defaultIfBlank(classIndex.getParserStatus(), CodeIndexConstants.ScanStatus.SUCCESS));
        entity.setConfidence(CodeIndexConstants.Confidence.HIGH);
        entity.setIsDeleted(false);
        entity.setVersion(0);
        return entity;
    }

    private CodePackageIndex toPackageEntity(Long indexId, Long scmConfigId, RepositoryCodeIndexDraft.PackageDraft packageDraft) {
        CodePackageIndex entity = new CodePackageIndex();
        entity.setIndexId(indexId);
        entity.setScmConfigId(scmConfigId);
        entity.setPackageName(packageDraft.getPackageName());
        entity.setModulePaths(JSON.toJSONString(packageDraft.getModulePaths()));
        entity.setPrimaryModulePath(packageDraft.getPrimaryModulePath());
        entity.setClassCount(defaultInt(packageDraft.getClassCount()));
        entity.setAmbiguous(Boolean.TRUE.equals(packageDraft.getAmbiguous()));
        entity.setConfidence(packageDraft.getConfidence());
        entity.setIsDeleted(false);
        entity.setVersion(0);
        return entity;
    }

    private CodeIndexSummaryResDTO toSummary(CodeRepositoryIndex entity) {
        if (entity == null) {
            return null;
        }
        CodeIndexSummaryResDTO response = new CodeIndexSummaryResDTO();
        response.setIndexId(entity.getId());
        response.setScmConfigId(entity.getScmConfigId());
        response.setScmProvider(entity.getScmProvider());
        response.setScmProjectId(entity.getScmProjectId());
        response.setRepoOwner(entity.getRepoOwner());
        response.setRepoName(entity.getRepoName());
        response.setBranchName(entity.getBranchName());
        response.setCommitSha(entity.getCommitSha());
        response.setIndexVersion(entity.getIndexVersion());
        response.setScanStatus(entity.getScanStatus());
        response.setScanType(entity.getScanType());
        response.setTriggerType(entity.getTriggerType());
        response.setModuleCount(entity.getModuleCount());
        response.setSourceRootCount(entity.getSourceRootCount());
        response.setJavaFileCount(entity.getJavaFileCount());
        response.setClassCount(entity.getClassCount());
        response.setPackageCount(entity.getPackageCount());
        response.setAmbiguousPackageCount(entity.getAmbiguousPackageCount());
        response.setWarningCount(entity.getWarningCount());
        response.setConfidence(entity.getConfidence());
        response.setStale(entity.getStale());
        response.setLatestErrorMessage(entity.getLatestErrorMessage());
        response.setStartedAt(entity.getStartedAt());
        response.setFinishedAt(entity.getFinishedAt());
        response.setCreateTime(entity.getCreateTime());
        response.setUpdateTime(entity.getUpdateTime());
        return response;
    }

    private CodeIndexSummaryResDTO buildFailedScanSummary(ScmConfig scmConfig,
                                                          CodeIndexScanReqDTO requestDTO,
                                                          String commitSha,
                                                          String errorMessage) {
        CodeIndexSummaryResDTO response = new CodeIndexSummaryResDTO();
        response.setScmConfigId(scmConfig.getId());
        response.setScmProvider(scmConfig.getScmProvider());
        response.setScmProjectId(scmConfig.getProjectId() == null ? null : String.valueOf(scmConfig.getProjectId()));
        response.setRepoOwner(scmConfig.getRepoOwner());
        response.setRepoName(scmConfig.getRepoName());
        response.setBranchName(defaultIfBlank(requestDTO.getBranchName(), CodeIndexConstants.DEFAULT_BRANCH));
        response.setCommitSha(commitSha);
        response.setIndexVersion(CodeIndexConstants.CURRENT_INDEX_VERSION);
        response.setScanStatus(CodeIndexConstants.ScanStatus.FAILED);
        response.setScanType(defaultIfBlank(requestDTO.getScanType(), CodeIndexConstants.ScanType.FULL));
        response.setTriggerType(CodeIndexConstants.TriggerType.MANUAL);
        response.setModuleCount(0);
        response.setSourceRootCount(0);
        response.setJavaFileCount(0);
        response.setClassCount(0);
        response.setPackageCount(0);
        response.setAmbiguousPackageCount(0);
        response.setWarningCount(1);
        response.setConfidence(CodeIndexConstants.Confidence.LOW);
        response.setStale(false);
        response.setLatestErrorMessage(errorMessage);
        LocalDateTime now = LocalDateTime.now();
        response.setStartedAt(now);
        response.setFinishedAt(now);
        return response;
    }

    private CodeIndexDetailResDTO.ModuleSummaryDTO toModuleSummary(CodeModuleIndex entity) {
        CodeIndexDetailResDTO.ModuleSummaryDTO response = new CodeIndexDetailResDTO.ModuleSummaryDTO();
        response.setModuleId(entity.getId());
        response.setModuleName(entity.getModuleName());
        response.setModulePath(entity.getModulePath());
        response.setParentModulePath(entity.getParentModulePath());
        response.setBuildType(entity.getBuildType());
        response.setPackaging(entity.getPackaging());
        response.setSourceRootsJson(entity.getSourceRoots());
        response.setJavaFileCount(entity.getJavaFileCount());
        response.setClassCount(entity.getClassCount());
        response.setScanStatus(entity.getScanStatus());
        response.setWarningMessage(entity.getWarningMessage());
        return response;
    }

    private CodeIndexDetailResDTO.PackageSummaryDTO toPackageSummary(CodePackageIndex entity) {
        CodeIndexDetailResDTO.PackageSummaryDTO response = new CodeIndexDetailResDTO.PackageSummaryDTO();
        response.setPackageName(entity.getPackageName());
        response.setPrimaryModulePath(entity.getPrimaryModulePath());
        response.setModulePathsJson(entity.getModulePaths());
        response.setClassCount(entity.getClassCount());
        response.setAmbiguous(entity.getAmbiguous());
        response.setConfidence(entity.getConfidence());
        return response;
    }

    private CodeClassIndexResDTO toClassRes(CodeClassIndex entity) {
        CodeClassIndexResDTO response = new CodeClassIndexResDTO();
        response.setId(entity.getId());
        response.setIndexId(entity.getIndexId());
        response.setScmConfigId(entity.getScmConfigId());
        response.setModulePath(entity.getModulePath());
        response.setSourceRoot(entity.getSourceRoot());
        response.setFilePath(entity.getFilePath());
        response.setFileSha(entity.getFileSha());
        response.setPackageName(entity.getPackageName());
        response.setClassName(entity.getClassName());
        response.setQualifiedName(entity.getQualifiedName());
        response.setClassKind(entity.getClassKind());
        response.setPrimaryType(entity.getPrimaryType());
        response.setLineStart(entity.getLineStart());
        response.setLineEnd(entity.getLineEnd());
        response.setImportsJson(entity.getImportsJson());
        response.setParserStatus(entity.getParserStatus());
        response.setConfidence(entity.getConfidence());
        return response;
    }

    private int countAmbiguousPackages(RepositoryCodeIndexDraft draft) {
        return (int) draft.getPackages().stream()
                .filter(packageDraft -> Boolean.TRUE.equals(packageDraft.getAmbiguous()))
                .count();
    }

    private String resolveConfidence(RepositoryCodeIndexDraft draft) {
        if (draft.getClassCount() == null || draft.getClassCount() == 0) {
            return CodeIndexConstants.Confidence.LOW;
        }
        return defaultInt(draft.getWarningCount()) > 0
                ? CodeIndexConstants.Confidence.MEDIUM
                : CodeIndexConstants.Confidence.HIGH;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String resolveCommitSha(CodeIndexScanReqDTO requestDTO) {
        if (requestDTO != null && hasText(requestDTO.getCommitSha())) {
            return requestDTO.getCommitSha().trim();
        }
        String branchName = defaultIfBlank(
                requestDTO == null ? null : requestDTO.getBranchName(),
                CodeIndexConstants.DEFAULT_BRANCH);
        String normalizedBranch = branchName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (normalizedBranch.length() > 40) {
            normalizedBranch = normalizedBranch.substring(0, 40);
        }
        return "unresolved-" + normalizedBranch + "-" + Integer.toUnsignedString(branchName.hashCode(), 16);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return hasText(value) ? value : defaultValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
