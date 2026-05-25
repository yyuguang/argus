package com.lnzz.argus.rule.controller;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.common.constant.SystemPermissionCodes;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.security.LoginUtil;
import com.lnzz.argus.security.RequirePermission;
import com.lnzz.argus.rule.dto.req.RuleProfileSaveReqDTO;
import com.lnzz.argus.rule.dto.res.RuleProfileResDTO;
import com.lnzz.argus.rule.service.RuleProfileService;
import com.lnzz.argus.system.service.PermissionDecisionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 规则配置管理 Controller。
 *
 * @author Fantasy
 * @date 2026/05/18 00:35
 */
@Validated
@RestController
@RequestMapping("/api/v1/rules/profiles/scm")
@RequiredArgsConstructor
public class RuleProfileController {

    private final RuleProfileService ruleProfileService;
    private final PermissionDecisionService permissionDecisionService;

    /**
     * 查询仓库级规则配置。
     *
     * @param scmConfigId SCM 仓库配置 ID
     * @return 仓库级规则配置
     */
    @GetMapping("/{scmConfigId}")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_VIEW)
    public Result<RuleProfileResDTO> getScmRuleProfile(@PathVariable Long scmConfigId) {
        return Result.success(ruleProfileService.getScmRuleProfile(scmConfigId));
    }

    /**
     * 保存仓库级规则配置。
     *
     * @param scmConfigId SCM 仓库配置 ID
     * @param requestDTO  规则配置保存请求
     * @return 保存后的仓库级规则配置
     */
    @PutMapping("/{scmConfigId}")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_VIEW)
    public Result<RuleProfileResDTO> saveScmRuleProfile(@PathVariable Long scmConfigId,
                                                        HttpServletRequest request,
                                                        @Valid @RequestBody RuleProfileSaveReqDTO requestDTO) {
        requestDTO.setScmConfigId(scmConfigId);
        validateUpdatePermissions(scmConfigId, requestDTO, request);
        return Result.success("规则配置保存成功", ruleProfileService.saveScmRuleProfile(scmConfigId, requestDTO));
    }

    private void validateUpdatePermissions(Long scmConfigId,
                                           RuleProfileSaveReqDTO requestDTO,
                                           HttpServletRequest request) {
        RuleProfileResDTO currentProfile = ruleProfileService.getScmRuleProfile(scmConfigId);
        boolean scoringChanged = hasScoringChanges(currentProfile, requestDTO);
        if (!scoringChanged) {
            return;
        }
        Long userId = LoginUtil.currentUserIdOrNull();
        if (userId == null) {
            throw new BizException(ResultCode.ADMIN_UNAUTHENTICATED);
        }
        String clientIp = request == null ? null : request.getRemoteAddr();
        ensurePermission(userId, clientIp, SystemPermissionCodes.RULE_MANAGEMENT_SCORING_UPDATE,
                "当前账号缺少评分配置更新权限");
    }

    private void ensurePermission(Long userId, String clientIp, String permissionCode, String message) {
        if (!permissionDecisionService.hasPermission(userId, permissionCode, clientIp)) {
            throw new BizException(ResultCode.ADMIN_FORBIDDEN, message);
        }
    }

    private boolean hasScoringChanges(RuleProfileResDTO currentProfile, RuleProfileSaveReqDTO requestDTO) {
        return !jsonEquals(currentProfile == null ? null : currentProfile.getScoringProfile(),
                requestDTO == null ? null : requestDTO.getScoringProfile())
                || !jsonEquals(currentProfile == null ? null : currentProfile.getRuleProfile(),
                requestDTO == null ? null : requestDTO.getRuleProfile());
    }

    private boolean jsonEquals(Object left, Object right) {
        return JSON.toJSONString(left).equals(JSON.toJSONString(right));
    }
}
