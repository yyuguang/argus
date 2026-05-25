package com.lnzz.argus.codeindex.service.impl;

import com.lnzz.argus.codeindex.dao.entity.AppVersionBinding;
import com.lnzz.argus.codeindex.dao.entity.CodeRepositoryIndex;
import com.lnzz.argus.codeindex.dao.mapper.AppVersionBindingMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodeRepositoryIndexMapper;
import com.lnzz.argus.codeindex.dto.req.AppVersionBindingReqDTO;
import com.lnzz.argus.codeindex.dto.res.AppVersionBindingResDTO;
import com.lnzz.argus.codeindex.service.AppVersionBindingService;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * @classname: AppVersionBindingServiceImpl
 * @author: Fantasy
 * @date: 2026/05/19 17:25
 * @description: 应用版本源码绑定服务实现，维护应用环境当前激活 commit 与源码索引的绑定关系。
 */
@Service
@RequiredArgsConstructor
public class AppVersionBindingServiceImpl implements AppVersionBindingService {

    private static final String INDEX_STATUS_BOUND = "BOUND";
    private static final String INDEX_STATUS_BUILDING = "BUILDING";

    private final AppVersionBindingMapper appVersionBindingMapper;
    private final CodeRepositoryIndexMapper repositoryIndexMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppVersionBindingResDTO bind(AppVersionBindingReqDTO requestDTO) {
        if (requestDTO == null) {
            return null;
        }
        AppVersionBinding activeBinding = appVersionBindingMapper.selectActiveBinding(
                requestDTO.getAppName(), requestDTO.getEnvironment(), requestDTO.getScmConfigId());
        AppVersionBinding commitBinding = appVersionBindingMapper.selectByCommit(
                requestDTO.getAppName(), requestDTO.getEnvironment(), requestDTO.getScmConfigId(), requestDTO.getCommitSha());
        CodeRepositoryIndex index = repositoryIndexMapper.selectByCommit(
                requestDTO.getScmConfigId(), requestDTO.getCommitSha(), CodeIndexConstants.CURRENT_INDEX_VERSION);

        if (activeBinding != null && !sameBinding(activeBinding, commitBinding)) {
            activeBinding.setActive(false);
            appVersionBindingMapper.updateById(activeBinding);
        }

        AppVersionBinding binding = commitBinding == null ? new AppVersionBinding() : commitBinding;
        fillBinding(binding, requestDTO, index);
        if (commitBinding == null) {
            appVersionBindingMapper.insert(binding);
        } else {
            appVersionBindingMapper.updateById(binding);
        }
        return toResponse(binding, index);
    }

    @Override
    public AppVersionBindingResDTO getActiveBinding(String appName, String environment, Long scmConfigId) {
        AppVersionBinding binding = appVersionBindingMapper.selectActiveBinding(appName, environment, scmConfigId);
        return toResponse(binding, null);
    }

    private void fillBinding(AppVersionBinding binding, AppVersionBindingReqDTO requestDTO, CodeRepositoryIndex index) {
        LocalDateTime now = LocalDateTime.now();
        binding.setMappingId(requestDTO.getMappingId());
        binding.setAppName(requestDTO.getAppName());
        binding.setEnvironment(requestDTO.getEnvironment());
        binding.setScmConfigId(requestDTO.getScmConfigId());
        binding.setBranchName(defaultIfBlank(requestDTO.getBranchName(), CodeIndexConstants.DEFAULT_BRANCH));
        binding.setCommitSha(requestDTO.getCommitSha());
        binding.setVersionName(requestDTO.getVersionName());
        binding.setIndexId(index == null ? null : index.getId());
        binding.setBindingSource(defaultIfBlank(requestDTO.getBindingSource(), CodeIndexConstants.TriggerType.MANUAL));
        binding.setActive(true);
        binding.setActivatedAt(now);
        binding.setLastSeenAt(now);
        binding.setRemark(requestDTO.getRemark());
        binding.setIsDeleted(false);
        binding.setVersion(binding.getVersion() == null ? 0 : binding.getVersion());
    }

    private AppVersionBindingResDTO toResponse(AppVersionBinding binding, CodeRepositoryIndex index) {
        if (binding == null) {
            return null;
        }
        AppVersionBindingResDTO response = new AppVersionBindingResDTO();
        response.setBindingId(binding.getId());
        response.setMappingId(binding.getMappingId());
        response.setAppName(binding.getAppName());
        response.setEnvironment(binding.getEnvironment());
        response.setScmConfigId(binding.getScmConfigId());
        response.setIndexId(binding.getIndexId());
        response.setBranchName(binding.getBranchName());
        response.setCommitSha(binding.getCommitSha());
        response.setVersionName(binding.getVersionName());
        response.setBindingSource(binding.getBindingSource());
        response.setIndexStatus(resolveIndexStatus(binding, index));
        response.setActive(binding.getActive());
        response.setActivatedAt(binding.getActivatedAt());
        response.setLastSeenAt(binding.getLastSeenAt());
        response.setCreateTime(binding.getCreateTime());
        response.setUpdateTime(binding.getUpdateTime());
        return response;
    }

    private String resolveIndexStatus(AppVersionBinding binding, CodeRepositoryIndex index) {
        if (index != null) {
            return index.getScanStatus();
        }
        return binding.getIndexId() == null ? INDEX_STATUS_BUILDING : INDEX_STATUS_BOUND;
    }

    private boolean sameBinding(AppVersionBinding activeBinding, AppVersionBinding commitBinding) {
        if (activeBinding == null || commitBinding == null) {
            return false;
        }
        return activeBinding.getId() != null && activeBinding.getId().equals(commitBinding.getId());
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
