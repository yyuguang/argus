package com.lnzz.argus.codeindex.service.impl;

import com.lnzz.argus.codeindex.dao.entity.AppVersionBinding;
import com.lnzz.argus.codeindex.dao.entity.CodeRepositoryIndex;
import com.lnzz.argus.codeindex.dao.mapper.AppVersionBindingMapper;
import com.lnzz.argus.codeindex.dao.mapper.CodeRepositoryIndexMapper;
import com.lnzz.argus.codeindex.dto.req.AppVersionBindingReqDTO;
import com.lnzz.argus.codeindex.dto.res.AppVersionBindingResDTO;
import com.lnzz.argus.codeindex.support.CodeIndexConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AppVersionBindingServiceImpl - 应用版本源码绑定")
class AppVersionBindingServiceImplTest {

    @Mock
    private AppVersionBindingMapper appVersionBindingMapper;
    @Mock
    private CodeRepositoryIndexMapper repositoryIndexMapper;

    private AppVersionBindingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppVersionBindingServiceImpl(appVersionBindingMapper, repositoryIndexMapper);
    }

    @Test
    @DisplayName("首次绑定时写入当前激活版本并关联已存在索引")
    void firstBindShouldCreateActiveBindingWithIndex() {
        AppVersionBindingReqDTO requestDTO = request("abc123");
        CodeRepositoryIndex index = repositoryIndex(100L, CodeIndexConstants.ScanStatus.SUCCESS);

        when(appVersionBindingMapper.selectActiveBinding("order-service", "prod", 1L)).thenReturn(null);
        when(appVersionBindingMapper.selectByCommit("order-service", "prod", 1L, "abc123")).thenReturn(null);
        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION)).thenReturn(index);
        when(appVersionBindingMapper.insert(any(AppVersionBinding.class))).thenAnswer(invocation -> {
            AppVersionBinding binding = invocation.getArgument(0);
            binding.setId(10L);
            return 1;
        });

        AppVersionBindingResDTO response = service.bind(requestDTO);

        assertEquals(10L, response.getBindingId());
        assertEquals(100L, response.getIndexId());
        assertEquals(CodeIndexConstants.ScanStatus.SUCCESS, response.getIndexStatus());
        assertTrue(response.getActive());
    }

    @Test
    @DisplayName("切换版本时会将旧激活绑定置为非激活")
    void switchVersionShouldDeactivateOldBinding() {
        AppVersionBinding oldBinding = activeBinding(9L, "old123");
        AppVersionBindingReqDTO requestDTO = request("new123");
        CodeRepositoryIndex index = repositoryIndex(101L, CodeIndexConstants.ScanStatus.SUCCESS);

        when(appVersionBindingMapper.selectActiveBinding("order-service", "prod", 1L)).thenReturn(oldBinding);
        when(appVersionBindingMapper.selectByCommit("order-service", "prod", 1L, "new123")).thenReturn(null);
        when(repositoryIndexMapper.selectByCommit(1L, "new123", CodeIndexConstants.CURRENT_INDEX_VERSION)).thenReturn(index);
        when(appVersionBindingMapper.updateById(oldBinding)).thenReturn(1);
        when(appVersionBindingMapper.insert(any(AppVersionBinding.class))).thenAnswer(invocation -> {
            AppVersionBinding binding = invocation.getArgument(0);
            binding.setId(11L);
            return 1;
        });

        AppVersionBindingResDTO response = service.bind(requestDTO);

        assertEquals(11L, response.getBindingId());
        assertEquals(101L, response.getIndexId());
        ArgumentCaptor<AppVersionBinding> oldCaptor = ArgumentCaptor.forClass(AppVersionBinding.class);
        verify(appVersionBindingMapper).updateById(oldCaptor.capture());
        assertEquals(false, oldCaptor.getValue().getActive());
    }

    @Test
    @DisplayName("目标 commit 暂无索引时允许先保存绑定并标记构建中")
    void bindShouldAllowCommitWithoutIndex() {
        AppVersionBindingReqDTO requestDTO = request("abc123");

        when(appVersionBindingMapper.selectActiveBinding("order-service", "prod", 1L)).thenReturn(null);
        when(appVersionBindingMapper.selectByCommit("order-service", "prod", 1L, "abc123")).thenReturn(null);
        when(repositoryIndexMapper.selectByCommit(1L, "abc123", CodeIndexConstants.CURRENT_INDEX_VERSION)).thenReturn(null);
        when(appVersionBindingMapper.insert(any(AppVersionBinding.class))).thenAnswer(invocation -> {
            AppVersionBinding binding = invocation.getArgument(0);
            binding.setId(12L);
            return 1;
        });

        AppVersionBindingResDTO response = service.bind(requestDTO);

        assertEquals(12L, response.getBindingId());
        assertNull(response.getIndexId());
        assertEquals("BUILDING", response.getIndexStatus());
    }

    @Test
    @DisplayName("支持查询应用环境当前激活绑定")
    void getActiveBindingShouldReturnCurrentBinding() {
        AppVersionBinding activeBinding = activeBinding(9L, "abc123");

        when(appVersionBindingMapper.selectActiveBinding("order-service", "prod", 1L)).thenReturn(activeBinding);

        AppVersionBindingResDTO response = service.getActiveBinding("order-service", "prod", 1L);

        assertEquals(9L, response.getBindingId());
        assertEquals("abc123", response.getCommitSha());
        assertEquals("BUILDING", response.getIndexStatus());
    }

    private AppVersionBindingReqDTO request(String commitSha) {
        AppVersionBindingReqDTO requestDTO = new AppVersionBindingReqDTO();
        requestDTO.setMappingId(20L);
        requestDTO.setAppName("order-service");
        requestDTO.setEnvironment("prod");
        requestDTO.setScmConfigId(1L);
        requestDTO.setBranchName("main");
        requestDTO.setCommitSha(commitSha);
        requestDTO.setVersionName("v1.0.0");
        requestDTO.setBindingSource("MANUAL");
        return requestDTO;
    }

    private AppVersionBinding activeBinding(Long id, String commitSha) {
        AppVersionBinding binding = new AppVersionBinding();
        binding.setId(id);
        binding.setMappingId(20L);
        binding.setAppName("order-service");
        binding.setEnvironment("prod");
        binding.setScmConfigId(1L);
        binding.setBranchName("main");
        binding.setCommitSha(commitSha);
        binding.setVersionName("v1.0.0");
        binding.setBindingSource("MANUAL");
        binding.setActive(true);
        return binding;
    }

    private CodeRepositoryIndex repositoryIndex(Long id, String status) {
        CodeRepositoryIndex index = new CodeRepositoryIndex();
        index.setId(id);
        index.setScanStatus(status);
        return index;
    }
}
