package com.lnzz.argus.codeindex.service.impl;

import com.lnzz.argus.codeindex.dao.entity.AppVersionBinding;
import com.lnzz.argus.codeindex.dao.entity.CodeClassIndex;
import com.lnzz.argus.codeindex.dao.entity.CodeRepositoryIndex;
import com.lnzz.argus.codeindex.dao.mapper.AppVersionBindingMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodeClassIndexMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodeRepositoryIndexMapper;
import com.lnzz.argus.codeindex.dto.req.SourceLocateReqDTO;
import com.lnzz.argus.codeindex.dto.res.SourceLocateResDTO;
import com.lnzz.argus.codeindex.service.SourceLocationService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @classname: SourceLocationServiceImpl
 * @author: Fantasy
 * @date: 2026/05/19 20:50
 * @description: 索引优先源码定位服务实现，基于 commit、应用版本绑定和最新成功索引定位 Java 源文件。
 */
@Service
@RequiredArgsConstructor
public class SourceLocationServiceImpl implements SourceLocationService {

    private final CodeRepositoryIndexMapper repositoryIndexMapper;
    private final CodeClassIndexMapper classIndexMapper;
    private final AppVersionBindingMapper appVersionBindingMapper;

    @Override
    public SourceLocateResDTO locate(SourceLocateReqDTO requestDTO) {
        SourceLocateReqDTO effectiveRequest = requestDTO == null ? new SourceLocateReqDTO() : requestDTO;
        SourceLocateResDTO response = notMatched();
        response.setLineNumber(effectiveRequest.getLineNumber());
        if (effectiveRequest.getScmConfigId() == null) {
            response.getWarnings().add("缺少 scmConfigId，无法查询源码索引");
            return response;
        }
        if (!hasAnyLocateKey(effectiveRequest)) {
            response.getWarnings().add("缺少 qualifiedName 或 filePath，无法定位源码");
            return response;
        }

        Map<Long, CodeRepositoryIndex> candidateIndexes = new LinkedHashMap<>();
        SourceLocateResDTO commitMatch = locateByCommitIndex(effectiveRequest, response, candidateIndexes);
        if (Boolean.TRUE.equals(commitMatch.getMatched())) {
            return commitMatch;
        }
        SourceLocateResDTO bindingMatch = locateByBindingIndex(effectiveRequest, response, candidateIndexes);
        if (Boolean.TRUE.equals(bindingMatch.getMatched())) {
            return bindingMatch;
        }
        SourceLocateResDTO latestMatch = locateByLatestIndex(effectiveRequest, response, candidateIndexes);
        if (Boolean.TRUE.equals(latestMatch.getMatched())) {
            return latestMatch;
        }
        SourceLocateResDTO simpleNameMatch = locateBySimpleName(new ArrayList<>(candidateIndexes.values()), effectiveRequest);
        simpleNameMatch.getWarnings().addAll(0, response.getWarnings());
        return simpleNameMatch;
    }

    private SourceLocateResDTO locateByCommitIndex(SourceLocateReqDTO requestDTO,
                                                   SourceLocateResDTO response,
                                                   Map<Long, CodeRepositoryIndex> candidateIndexes) {
        CodeRepositoryIndex commitIndex = resolveCommitIndex(requestDTO, response);
        return locateInCandidateIndex(commitIndex, requestDTO, response, candidateIndexes);
    }

    private SourceLocateResDTO locateByBindingIndex(SourceLocateReqDTO requestDTO,
                                                    SourceLocateResDTO response,
                                                    Map<Long, CodeRepositoryIndex> candidateIndexes) {
        CodeRepositoryIndex bindingIndex = resolveBindingIndex(requestDTO, response);
        return locateInCandidateIndex(bindingIndex, requestDTO, response, candidateIndexes);
    }

    private SourceLocateResDTO locateByLatestIndex(SourceLocateReqDTO requestDTO,
                                                   SourceLocateResDTO response,
                                                   Map<Long, CodeRepositoryIndex> candidateIndexes) {
        CodeRepositoryIndex latestIndex = repositoryIndexMapper.selectLatestSuccessful(
                requestDTO.getScmConfigId(),
                defaultIfBlank(requestDTO.getBranchName(), CodeIndexConstants.DEFAULT_BRANCH));
        if (latestIndex == null) {
            response.getWarnings().add("未找到默认分支最新成功源码索引");
        }
        return locateInCandidateIndex(latestIndex, requestDTO, response, candidateIndexes);
    }

    private SourceLocateResDTO locateInCandidateIndex(CodeRepositoryIndex index,
                                                      SourceLocateReqDTO requestDTO,
                                                      SourceLocateResDTO response,
                                                      Map<Long, CodeRepositoryIndex> candidateIndexes) {
        putIndex(candidateIndexes, index);
        if (!isSuccessfulIndex(index)) {
            return notMatched();
        }
        SourceLocateResDTO match = locateByQualifiedNameOrFilePath(index, requestDTO);
        if (Boolean.TRUE.equals(match.getMatched())) {
            match.getWarnings().addAll(response.getWarnings());
        }
        return match;
    }

    private CodeRepositoryIndex resolveCommitIndex(SourceLocateReqDTO requestDTO, SourceLocateResDTO response) {
        if (!hasText(requestDTO.getCommitSha())) {
            return null;
        }
        CodeRepositoryIndex index = repositoryIndexMapper.selectByCommit(
                requestDTO.getScmConfigId(),
                requestDTO.getCommitSha(),
                CodeIndexConstants.CURRENT_INDEX_VERSION);
        if (isSuccessfulIndex(index)) {
            return index;
        }
        response.getWarnings().add("未找到 commit 对应的成功源码索引: " + requestDTO.getCommitSha());
        return null;
    }

    private CodeRepositoryIndex resolveBindingIndex(SourceLocateReqDTO requestDTO, SourceLocateResDTO response) {
        if (!hasText(requestDTO.getAppName()) || !hasText(requestDTO.getEnvironment())) {
            return null;
        }
        AppVersionBinding binding = appVersionBindingMapper.selectActiveBinding(
                requestDTO.getAppName(),
                requestDTO.getEnvironment(),
                requestDTO.getScmConfigId());
        if (binding == null) {
            response.getWarnings().add("未找到应用环境当前激活源码版本绑定");
            return null;
        }
        CodeRepositoryIndex index = null;
        if (binding.getIndexId() != null) {
            index = repositoryIndexMapper.selectById(binding.getIndexId());
        }
        if (!isSuccessfulIndex(index) && hasText(binding.getCommitSha())) {
            index = repositoryIndexMapper.selectByCommit(
                    binding.getScmConfigId(),
                    binding.getCommitSha(),
                    CodeIndexConstants.CURRENT_INDEX_VERSION);
        }
        if (isSuccessfulIndex(index)) {
            return index;
        }
        response.getWarnings().add("应用版本绑定未关联成功源码索引: commitSha=" + binding.getCommitSha());
        return null;
    }

    private SourceLocateResDTO locateByQualifiedNameOrFilePath(CodeRepositoryIndex index,
                                                               SourceLocateReqDTO requestDTO) {
        if (hasText(requestDTO.getQualifiedName()) && requestDTO.getQualifiedName().contains(".")) {
            List<CodeClassIndex> classes = classIndexMapper.selectByQualifiedName(index.getId(), requestDTO.getQualifiedName());
            SourceLocateResDTO result = matchFromCandidates(index, classes, CodeIndexConstants.MatchType.QUALIFIED_NAME, requestDTO);
            if (Boolean.TRUE.equals(result.getMatched())) {
                return result;
            }
        }
        if (hasText(requestDTO.getFilePath())) {
            List<CodeClassIndex> classes = classIndexMapper.selectByFilePath(index.getId(), requestDTO.getFilePath());
            SourceLocateResDTO result = matchFromCandidates(index, classes, CodeIndexConstants.MatchType.FILE_PATH, requestDTO);
            if (Boolean.TRUE.equals(result.getMatched())) {
                return result;
            }
        }
        return notMatched();
    }

    private SourceLocateResDTO locateBySimpleName(List<CodeRepositoryIndex> indexes, SourceLocateReqDTO requestDTO) {
        String simpleName = resolveSimpleName(requestDTO);
        if (!hasText(simpleName)) {
            SourceLocateResDTO response = notMatched();
            response.getWarnings().add("未找到可用于简单类名定位的名称");
            return response;
        }
        for (CodeRepositoryIndex index : indexes) {
            List<CodeClassIndex> classes = classIndexMapper.selectByClassName(index.getId(), simpleName);
            if (!classes.isEmpty()) {
                return matchFromCandidates(index, classes, CodeIndexConstants.MatchType.SIMPLE_NAME, requestDTO);
            }
        }
        SourceLocateResDTO response = notMatched();
        response.getWarnings().add("源码索引未命中: " + simpleName);
        return response;
    }

    private SourceLocateResDTO matchFromCandidates(CodeRepositoryIndex index,
                                                   List<CodeClassIndex> classes,
                                                   String matchType,
                                                   SourceLocateReqDTO requestDTO) {
        SourceLocateResDTO response = notMatched();
        response.setIndexId(index.getId());
        response.setCommitSha(index.getCommitSha());
        response.setMatchType(matchType);
        response.setLineNumber(requestDTO.getLineNumber());
        if (classes == null || classes.isEmpty()) {
            return response;
        }
        response.setCandidates(classes.stream()
                .map(classIndex -> toCandidate(index, classIndex, matchType))
                .toList());
        if (classes.size() > 1 && CodeIndexConstants.MatchType.SIMPLE_NAME.equals(matchType)) {
            response.setConfidence(CodeIndexConstants.Confidence.MEDIUM);
            response.getWarnings().add("简单类名存在多个候选，需结合包名或文件路径确认");
            return response;
        }
        CodeClassIndex selected = selectPrimary(classes);
        fillMatched(response, index, selected, matchType, requestDTO.getLineNumber());
        if (classes.size() > 1) {
            response.getWarnings().add("同一定位条件存在多个类型候选，已优先选择主类型");
        }
        return response;
    }

    private void fillMatched(SourceLocateResDTO response,
                             CodeRepositoryIndex index,
                             CodeClassIndex classIndex,
                             String matchType,
                             Integer lineNumber) {
        response.setMatched(true);
        response.setConfidence(defaultIfBlank(classIndex.getConfidence(), CodeIndexConstants.Confidence.HIGH));
        response.setMatchType(matchType);
        response.setIndexId(index.getId());
        response.setCommitSha(index.getCommitSha());
        response.setModulePath(classIndex.getModulePath());
        response.setSourceRoot(classIndex.getSourceRoot());
        response.setFilePath(classIndex.getFilePath());
        response.setPackageName(classIndex.getPackageName());
        response.setClassName(classIndex.getClassName());
        response.setQualifiedName(classIndex.getQualifiedName());
        response.setLineNumber(lineNumber);
    }

    private CodeClassIndex selectPrimary(List<CodeClassIndex> classes) {
        return classes.stream()
                .filter(classIndex -> Boolean.TRUE.equals(classIndex.getPrimaryType()))
                .findFirst()
                .orElse(classes.get(0));
    }

    private SourceLocateResDTO.CandidateDTO toCandidate(CodeRepositoryIndex index,
                                                        CodeClassIndex classIndex,
                                                        String matchType) {
        SourceLocateResDTO.CandidateDTO candidate = new SourceLocateResDTO.CandidateDTO();
        candidate.setIndexId(index.getId());
        candidate.setCommitSha(index.getCommitSha());
        candidate.setModulePath(classIndex.getModulePath());
        candidate.setSourceRoot(classIndex.getSourceRoot());
        candidate.setFilePath(classIndex.getFilePath());
        candidate.setPackageName(classIndex.getPackageName());
        candidate.setClassName(classIndex.getClassName());
        candidate.setQualifiedName(classIndex.getQualifiedName());
        candidate.setConfidence(defaultIfBlank(classIndex.getConfidence(), CodeIndexConstants.Confidence.HIGH));
        candidate.setMatchType(matchType);
        return candidate;
    }

    private void putIndex(Map<Long, CodeRepositoryIndex> indexes, CodeRepositoryIndex index) {
        if (isSuccessfulIndex(index) && index.getId() != null) {
            indexes.putIfAbsent(index.getId(), index);
        }
    }

    private boolean isSuccessfulIndex(CodeRepositoryIndex index) {
        return index != null && CodeIndexConstants.ScanStatus.SUCCESS.equals(index.getScanStatus());
    }

    private String resolveSimpleName(SourceLocateReqDTO requestDTO) {
        if (hasText(requestDTO.getQualifiedName())) {
            String qualifiedName = requestDTO.getQualifiedName().trim();
            int dotIndex = qualifiedName.lastIndexOf('.');
            return dotIndex >= 0 ? qualifiedName.substring(dotIndex + 1) : qualifiedName;
        }
        if (hasText(requestDTO.getFilePath())) {
            String filePath = requestDTO.getFilePath().trim();
            int slashIndex = filePath.lastIndexOf('/');
            String fileName = slashIndex >= 0 ? filePath.substring(slashIndex + 1) : filePath;
            int dotIndex = fileName.lastIndexOf('.');
            return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
        }
        return "";
    }

    private boolean hasAnyLocateKey(SourceLocateReqDTO requestDTO) {
        return hasText(requestDTO.getQualifiedName()) || hasText(requestDTO.getFilePath());
    }

    private SourceLocateResDTO notMatched() {
        SourceLocateResDTO response = new SourceLocateResDTO();
        response.setMatched(false);
        response.setConfidence(CodeIndexConstants.Confidence.NONE);
        response.setMatchType(CodeIndexConstants.MatchType.NONE);
        return response;
    }

    private String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
