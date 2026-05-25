package com.lnzz.argus.rule.controller;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.rule.dto.req.RuleProfileSaveReqDTO;
import com.lnzz.argus.rule.dto.res.RuleProfileResDTO;
import com.lnzz.argus.rule.service.RuleProfileService;
import com.lnzz.argus.security.CurrentUser;
import com.lnzz.argus.security.CurrentUserContext;
import com.lnzz.argus.system.service.PermissionDecisionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RuleProfileController - 规则配置接口")
class RuleProfileControllerTest {

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    @DisplayName("查询接口直接返回仓库级规则配置")
    void getScmRuleProfileReturnsServiceData() {
        RuleProfileService ruleProfileService = mock(RuleProfileService.class);
        PermissionDecisionService permissionDecisionService = mock(PermissionDecisionService.class);
        RuleProfileController controller = new RuleProfileController(ruleProfileService, permissionDecisionService);
        RuleProfileResDTO profile = new RuleProfileResDTO();
        profile.setScmConfigId(8L);
        when(ruleProfileService.getScmRuleProfile(8L)).thenReturn(profile);

        Result<RuleProfileResDTO> result = controller.getScmRuleProfile(8L);

        assertEquals(0, result.getCode());
        assertEquals(8L, result.getData().getScmConfigId());
    }

    @Test
    @DisplayName("保存接口会写回路径中的 scmConfigId")
    void saveScmRuleProfileAssignsPathScmConfigId() {
        RuleProfileService ruleProfileService = mock(RuleProfileService.class);
        PermissionDecisionService permissionDecisionService = mock(PermissionDecisionService.class);
        RuleProfileController controller = new RuleProfileController(ruleProfileService, permissionDecisionService);
        RuleProfileSaveReqDTO requestDTO = new RuleProfileSaveReqDTO();
        RuleProfileResDTO currentProfile = new RuleProfileResDTO();
        currentProfile.setScmConfigId(18L);
        RuleProfileResDTO savedProfile = new RuleProfileResDTO();
        savedProfile.setScmConfigId(18L);
        when(ruleProfileService.getScmRuleProfile(18L)).thenReturn(currentProfile);
        when(ruleProfileService.saveScmRuleProfile(anyLong(), any())).thenReturn(savedProfile);

        Result<RuleProfileResDTO> result = controller.saveScmRuleProfile(18L, null, requestDTO);

        assertEquals("规则配置保存成功", result.getMessage());
        ArgumentCaptor<RuleProfileSaveReqDTO> captor = ArgumentCaptor.forClass(RuleProfileSaveReqDTO.class);
        verify(ruleProfileService).saveScmRuleProfile(anyLong(), captor.capture());
        assertEquals(18L, captor.getValue().getScmConfigId());
        verify(permissionDecisionService, never()).hasPermission(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("评分变更且无权限时拒绝保存")
    void saveScmRuleProfileRejectsScoringUpdateWithoutPermission() {
        RuleProfileService ruleProfileService = mock(RuleProfileService.class);
        PermissionDecisionService permissionDecisionService = mock(PermissionDecisionService.class);
        RuleProfileController controller = new RuleProfileController(ruleProfileService, permissionDecisionService);
        RuleProfileResDTO currentProfile = new RuleProfileResDTO();
        RuleProfileSaveReqDTO requestDTO = new RuleProfileSaveReqDTO();
        requestDTO.getScoringProfile().setBlockThreshold(75);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(ruleProfileService.getScmRuleProfile(21L)).thenReturn(currentProfile);
        when(permissionDecisionService.hasPermission(1001L, "rule:management:scoring:update", "127.0.0.1"))
                .thenReturn(false);
        CurrentUserContext.set(new CurrentUser(
                1001L,
                "readonly",
                "Readonly User",
                "127.0.0.1",
                "JUnit",
                1L,
                List.of("QUALITY_VIEWER")));

        BizException exception = assertThrows(BizException.class,
                () -> controller.saveScmRuleProfile(21L, request, requestDTO));

        assertEquals(ResultCode.ADMIN_FORBIDDEN.getCode(), exception.getCode());
        assertEquals("当前账号缺少评分配置更新权限", exception.getMessage());
        verify(ruleProfileService, never()).saveScmRuleProfile(anyLong(), any());
    }
}
