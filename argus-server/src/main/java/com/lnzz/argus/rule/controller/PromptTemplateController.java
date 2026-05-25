package com.lnzz.argus.rule.controller;

import com.lnzz.argus.common.constant.SystemPermissionCodes;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.rule.dto.req.PromptTemplateSaveReqDTO;
import com.lnzz.argus.rule.dto.res.PromptCatalogCategoryResDTO;
import com.lnzz.argus.rule.dto.res.PromptTemplateSchemeResDTO;
import com.lnzz.argus.rule.service.PromptCatalogService;
import com.lnzz.argus.rule.service.PromptTemplateService;
import com.lnzz.argus.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Prompt 模板管理 Controller。
 *
 * @author Fantasy
 * @date 2026/05/18 22:02
 */
@Validated
@RestController
@RequestMapping("/api/v1/rules/prompts")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptCatalogService promptCatalogService;
    private final PromptTemplateService promptTemplateService;

    /**
     * 查询 Prompt 模板目录。
     *
     * @param category 一级分类，可为空
     * @param keyword  模板关键字，可为空
     * @return Prompt 模板目录
     */
    @GetMapping("/catalog")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_VIEW)
    public Result<List<PromptCatalogCategoryResDTO>> getCatalog(@RequestParam(required = false) String category,
                                                                @RequestParam(required = false) String keyword) {
        return Result.success(promptCatalogService.listCatalog(category, keyword));
    }

    /**
     * 查询全局 Prompt 方案列表。
     *
     * @param category 一级分类，可为空
     * @param keyword  模板关键字，可为空
     * @return 全局 Prompt 方案列表
     */
    @GetMapping("/global")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_VIEW)
    public Result<List<PromptTemplateSchemeResDTO>> listGlobalSchemes(@RequestParam(required = false) String category,
                                                                      @RequestParam(required = false) String keyword) {
        return Result.success(promptTemplateService.listGlobalSchemes(category, keyword));
    }

    /**
     * 查询仓库级 Prompt 方案列表。
     *
     * @param scmConfigId SCM 仓库配置 ID
     * @param category    一级分类，可为空
     * @param keyword     模板关键字，可为空
     * @return 仓库级 Prompt 方案列表
     */
    @GetMapping("/scm/{scmConfigId}")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_VIEW)
    public Result<List<PromptTemplateSchemeResDTO>> listScmSchemes(@PathVariable Long scmConfigId,
                                                                   @RequestParam(required = false) String category,
                                                                   @RequestParam(required = false) String keyword) {
        return Result.success(promptTemplateService.listScmSchemes(scmConfigId, category, keyword));
    }

    /**
     * 查询单个 Prompt 模板组详情。
     *
     * @param templateCode 模板编码
     * @param scope        查询作用域
     * @param scmConfigId  SCM 仓库配置 ID，可为空
     * @return Prompt 模板详情
     */
    @GetMapping("/{templateCode}")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_VIEW)
    public Result<PromptTemplateSchemeResDTO> getTemplateDetail(@PathVariable String templateCode,
                                                                @RequestParam String scope,
                                                                @RequestParam(required = false) Long scmConfigId) {
        return Result.success(promptTemplateService.getTemplateDetail(templateCode, scope, scmConfigId));
    }

    /**
     * 查询 Prompt 模板组的最终生效方案。
     *
     * @param templateCode 模板编码
     * @param scmConfigId  SCM 仓库配置 ID，可为空
     * @return 最终生效方案
     */
    @GetMapping("/{templateCode}/effective")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_VIEW)
    public Result<PromptTemplateSchemeResDTO> getEffectiveTemplate(@PathVariable String templateCode,
                                                                   @RequestParam(required = false) Long scmConfigId) {
        return Result.success(promptTemplateService.getEffectiveTemplate(templateCode, scmConfigId));
    }

    /**
     * 保存全局 Prompt 方案。
     *
     * @param templateCode 模板编码
     * @param requestDTO   保存请求
     * @return 保存后的方案详情
     */
    @PutMapping("/global/{templateCode}")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_PROMPT_UPDATE)
    public Result<PromptTemplateSchemeResDTO> saveGlobalScheme(@PathVariable String templateCode,
                                                               @Valid @RequestBody PromptTemplateSaveReqDTO requestDTO) {
        return Result.success("全局 Prompt 模板保存成功",
                promptTemplateService.saveGlobalScheme(templateCode, requestDTO));
    }

    /**
     * 保存仓库级 Prompt 覆盖方案。
     *
     * @param scmConfigId  SCM 仓库配置 ID
     * @param templateCode 模板编码
     * @param requestDTO   保存请求
     * @return 保存后的方案详情
     */
    @PutMapping("/scm/{scmConfigId}/{templateCode}")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_PROMPT_UPDATE)
    public Result<PromptTemplateSchemeResDTO> saveScmScheme(@PathVariable Long scmConfigId,
                                                            @PathVariable String templateCode,
                                                            @Valid @RequestBody PromptTemplateSaveReqDTO requestDTO) {
        return Result.success("仓库级 Prompt 模板保存成功",
                promptTemplateService.saveScmScheme(scmConfigId, templateCode, requestDTO));
    }

    /**
     * 删除仓库级 Prompt 覆盖方案。
     *
     * @param scmConfigId  SCM 仓库配置 ID
     * @param templateCode 模板编码
     * @return 删除结果
     */
    @DeleteMapping("/scm/{scmConfigId}/{templateCode}")
    @RequirePermission(SystemPermissionCodes.RULE_MANAGEMENT_PROMPT_UPDATE)
    public Result<Map<String, Object>> deleteScmOverride(@PathVariable Long scmConfigId,
                                                         @PathVariable String templateCode) {
        promptTemplateService.deleteScmOverride(scmConfigId, templateCode);
        return Result.success("仓库级 Prompt 覆盖已恢复全局兜底",
                Map.of("scmConfigId", scmConfigId, "templateCode", templateCode));
    }
}
