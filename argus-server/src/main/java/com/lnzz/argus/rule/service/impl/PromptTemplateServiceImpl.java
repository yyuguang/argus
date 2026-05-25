package com.lnzz.argus.rule.service.impl;

import com.lnzz.argus.common.constant.SystemDataConstants;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.rule.dao.entity.PromptTemplateDefinition;
import com.lnzz.argus.rule.dao.entity.PromptTemplateScheme;
import com.lnzz.argus.rule.dao.mapper.PromptTemplateDefinitionMapper;
import com.lnzz.argus.rule.dao.mapper.PromptTemplateSchemeMapper;
import com.lnzz.argus.rule.dto.req.PromptTemplateSaveReqDTO;
import com.lnzz.argus.rule.dto.res.PromptTemplateSchemeResDTO;
import com.lnzz.argus.rule.service.PromptTemplateService;
import com.lnzz.argus.scm.service.ScmConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @classname: PromptTemplateServiceImpl
 * @author: Fantasy
 * @date: 2026/05/18 22:02
 * @description: Prompt 模板方案服务实现，负责全局兜底方案和仓库级覆盖方案管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private static final String SCOPE_GLOBAL = "GLOBAL";
    private static final String SCOPE_SCM = "SCM";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final Long GLOBAL_SCM_CONFIG_ID = 0L;

    private final PromptTemplateDefinitionMapper promptTemplateDefinitionMapper;
    private final PromptTemplateSchemeMapper promptTemplateSchemeMapper;
    private final ScmConfigService scmConfigService;

    @Override
    public List<PromptTemplateSchemeResDTO> listGlobalSchemes(String category, String keyword) {
        List<PromptTemplateDefinition> definitions = queryDefinitions(category, keyword);
        Map<String, PromptTemplateScheme> globalMap = toSchemeMap(promptTemplateSchemeMapper.selectActiveGlobalSchemes());
        return definitions.stream()
                .map(definition -> buildSchemeResponse(definition,
                        globalMap.get(definition.getTemplateCode()),
                        globalMap.get(definition.getTemplateCode()),
                        SCOPE_GLOBAL,
                        GLOBAL_SCM_CONFIG_ID))
                .toList();
    }

    @Override
    public List<PromptTemplateSchemeResDTO> listScmSchemes(Long scmConfigId, String category, String keyword) {
        scmConfigService.requireById(scmConfigId);
        List<PromptTemplateDefinition> definitions = queryDefinitions(category, keyword);
        Map<String, PromptTemplateScheme> globalMap = toSchemeMap(promptTemplateSchemeMapper.selectActiveGlobalSchemes());
        Map<String, PromptTemplateScheme> scmMap = toSchemeMap(promptTemplateSchemeMapper.selectActiveScmOverrides(scmConfigId));
        return definitions.stream()
                .map(definition -> {
                    PromptTemplateScheme currentScheme = scmMap.get(definition.getTemplateCode());
                    PromptTemplateScheme effectiveScheme = currentScheme != null
                            ? currentScheme
                            : globalMap.get(definition.getTemplateCode());
                    return buildSchemeResponse(definition, currentScheme, effectiveScheme, SCOPE_SCM, scmConfigId);
                })
                .toList();
    }

    @Override
    public PromptTemplateSchemeResDTO getTemplateDetail(String templateCode, String scope, Long scmConfigId) {
        PromptTemplateDefinition definition = requireDefinition(templateCode);
        String normalizedScope = normalizeScope(scope);
        return switch (normalizedScope) {
            case SCOPE_GLOBAL -> buildSchemeResponse(
                    definition,
                    promptTemplateSchemeMapper.selectActiveByTemplateCode(definition.getTemplateCode(), SCOPE_GLOBAL, GLOBAL_SCM_CONFIG_ID),
                    promptTemplateSchemeMapper.selectActiveByTemplateCode(definition.getTemplateCode(), SCOPE_GLOBAL, GLOBAL_SCM_CONFIG_ID),
                    SCOPE_GLOBAL,
                    GLOBAL_SCM_CONFIG_ID);
            case SCOPE_SCM -> {
                Long effectiveScmConfigId = requireScmConfigId(scmConfigId);
                PromptTemplateScheme currentScheme = promptTemplateSchemeMapper.selectActiveByTemplateCode(
                        definition.getTemplateCode(), SCOPE_SCM, effectiveScmConfigId);
                PromptTemplateScheme globalScheme = promptTemplateSchemeMapper.selectActiveByTemplateCode(
                        definition.getTemplateCode(), SCOPE_GLOBAL, GLOBAL_SCM_CONFIG_ID);
                PromptTemplateScheme effectiveScheme = currentScheme != null ? currentScheme : globalScheme;
                yield buildSchemeResponse(definition, currentScheme, effectiveScheme, SCOPE_SCM, effectiveScmConfigId);
            }
            default -> throw new BizException(ResultCode.PARAM_ERROR, "不支持的 Prompt 作用域: " + scope);
        };
    }

    @Override
    public PromptTemplateSchemeResDTO getEffectiveTemplate(String templateCode, Long scmConfigId) {
        PromptTemplateDefinition definition = requireDefinition(templateCode);
        PromptTemplateScheme globalScheme = promptTemplateSchemeMapper.selectActiveByTemplateCode(
                definition.getTemplateCode(), SCOPE_GLOBAL, GLOBAL_SCM_CONFIG_ID);
        PromptTemplateScheme scmScheme = null;
        if (scmConfigId != null) {
            scmConfigService.requireById(scmConfigId);
            scmScheme = promptTemplateSchemeMapper.selectActiveByTemplateCode(definition.getTemplateCode(), SCOPE_SCM, scmConfigId);
        }
        PromptTemplateScheme effectiveScheme = scmScheme != null ? scmScheme : globalScheme;
        String currentScope = effectiveScheme == null ? SCOPE_GLOBAL : effectiveScheme.getScope();
        Long currentScmConfigId = effectiveScheme == null ? GLOBAL_SCM_CONFIG_ID : effectiveScheme.getScmConfigId();
        return buildSchemeResponse(definition, effectiveScheme, effectiveScheme, currentScope, currentScmConfigId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateSchemeResDTO saveGlobalScheme(String templateCode, PromptTemplateSaveReqDTO requestDTO) {
        PromptTemplateDefinition definition = requireDefinition(templateCode);
        validateSaveRequest(requestDTO, definition.getTemplateCode());
        saveScheme(definition, SCOPE_GLOBAL, GLOBAL_SCM_CONFIG_ID, requestDTO);
        log.info("保存全局 Prompt 模板方案成功, templateCode={}", definition.getTemplateCode());
        return getTemplateDetail(definition.getTemplateCode(), SCOPE_GLOBAL, GLOBAL_SCM_CONFIG_ID);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromptTemplateSchemeResDTO saveScmScheme(Long scmConfigId, String templateCode, PromptTemplateSaveReqDTO requestDTO) {
        PromptTemplateDefinition definition = requireDefinition(templateCode);
        if (!Boolean.TRUE.equals(definition.getSupportScmOverride())) {
            throw new BizException(ResultCode.PARAM_ERROR, "当前 Prompt 模板组不支持仓库级覆盖: " + templateCode);
        }
        validateSaveRequest(requestDTO, definition.getTemplateCode());
        Long effectiveScmConfigId = requireScmConfigId(scmConfigId);
        saveScheme(definition, SCOPE_SCM, effectiveScmConfigId, requestDTO);
        log.info("保存仓库级 Prompt 模板方案成功, scmConfigId={}, templateCode={}", effectiveScmConfigId, templateCode);
        return getTemplateDetail(definition.getTemplateCode(), SCOPE_SCM, effectiveScmConfigId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteScmOverride(Long scmConfigId, String templateCode) {
        PromptTemplateDefinition definition = requireDefinition(templateCode);
        Long effectiveScmConfigId = requireScmConfigId(scmConfigId);
        PromptTemplateScheme existing = promptTemplateSchemeMapper.selectAnyByTemplateCode(
                definition.getTemplateCode(), SCOPE_SCM, effectiveScmConfigId);
        if (existing == null) {
            return;
        }
        existing.setStatus(STATUS_DISABLED);
        promptTemplateSchemeMapper.updateById(existing);
        log.info("删除仓库级 Prompt 模板覆盖成功, scmConfigId={}, templateCode={}", effectiveScmConfigId, templateCode);
    }

    private List<PromptTemplateDefinition> queryDefinitions(String category, String keyword) {
        return promptTemplateDefinitionMapper.selectActiveCatalog(normalizeCategory(category), trimToNull(keyword));
    }

    private PromptTemplateDefinition requireDefinition(String templateCode) {
        String normalizedTemplateCode = normalizeTemplateCode(templateCode);
        PromptTemplateDefinition definition = promptTemplateDefinitionMapper.selectActiveByTemplateCode(normalizedTemplateCode);
        if (definition == null) {
            throw new BizException(ResultCode.NOT_FOUND, "Prompt 模板定义不存在: " + templateCode);
        }
        return definition;
    }

    private void validateSaveRequest(PromptTemplateSaveReqDTO requestDTO, String templateCode) {
        if (requestDTO == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "Prompt 模板保存请求不能为空");
        }
        if (!hasText(requestDTO.getContentText())) {
            throw new BizException(ResultCode.PARAM_ERROR, "Prompt 模板正文不能为空");
        }
        if (hasText(requestDTO.getTemplateCode())
                && !normalizeTemplateCode(requestDTO.getTemplateCode()).equals(templateCode)) {
            throw new BizException(ResultCode.PARAM_ERROR, "请求体中的 templateCode 与路径参数不一致");
        }
    }

    private void saveScheme(PromptTemplateDefinition definition,
                            String scope,
                            Long scmConfigId,
                            PromptTemplateSaveReqDTO requestDTO) {
        PromptTemplateScheme existing = promptTemplateSchemeMapper.selectAnyByTemplateCode(
                definition.getTemplateCode(), scope, scmConfigId);
        if (existing == null) {
            existing = new PromptTemplateScheme();
            existing.setTemplateCode(definition.getTemplateCode());
            existing.setScope(scope);
            existing.setScmConfigId(scmConfigId);
            existing.setIsDeleted(SystemDataConstants.NOT_DELETED);
        }
        existing.setContentText(requestDTO.getContentText().trim());
        existing.setRemark(trimToNull(requestDTO.getRemark()));
        existing.setStatus(normalizeStatus(requestDTO.getStatus()));
        existing.setIsDeleted(SystemDataConstants.NOT_DELETED);
        if (existing.getId() == null) {
            promptTemplateSchemeMapper.insert(existing);
        } else {
            promptTemplateSchemeMapper.updateById(existing);
        }
    }

    private PromptTemplateSchemeResDTO buildSchemeResponse(PromptTemplateDefinition definition,
                                                           PromptTemplateScheme currentScheme,
                                                           PromptTemplateScheme effectiveScheme,
                                                           String currentScope,
                                                           Long currentScmConfigId) {
        PromptTemplateSchemeResDTO response = new PromptTemplateSchemeResDTO();
        response.setTemplateCode(definition.getTemplateCode());
        response.setTemplateName(definition.getTemplateName());
        response.setCategory(definition.getCategory());
        response.setTemplateScene(definition.getTemplateScene());
        response.setSupportScmOverride(definition.getSupportScmOverride());
        response.setDescription(definition.getDescription());
        response.setCurrentScope(currentScope);
        response.setCurrentScmConfigId(currentScmConfigId);
        response.setHasScmOverride(SCOPE_SCM.equals(currentScope) && currentScheme != null);
        if (currentScheme != null) {
            response.setContentText(currentScheme.getContentText());
            response.setRemark(currentScheme.getRemark());
            response.setStatus(currentScheme.getStatus());
            response.setUpdateBy(currentScheme.getUpdateBy());
            response.setUpdateTime(currentScheme.getUpdateTime());
        }
        if (effectiveScheme != null) {
            response.setEffectiveScope(effectiveScheme.getScope());
            response.setEffectiveScmConfigId(effectiveScheme.getScmConfigId());
            if (currentScheme == null) {
                response.setStatus(effectiveScheme.getStatus());
                response.setUpdateBy(effectiveScheme.getUpdateBy());
                response.setUpdateTime(effectiveScheme.getUpdateTime());
            }
        } else {
            response.setEffectiveScope(null);
            response.setEffectiveScmConfigId(null);
        }
        return response;
    }

    private Map<String, PromptTemplateScheme> toSchemeMap(List<PromptTemplateScheme> schemes) {
        Map<String, PromptTemplateScheme> map = new LinkedHashMap<>();
        for (PromptTemplateScheme scheme : schemes) {
            map.putIfAbsent(scheme.getTemplateCode(), scheme);
        }
        return map;
    }

    private Long requireScmConfigId(Long scmConfigId) {
        if (scmConfigId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "SCM 作用域 Prompt 模板必须传 scmConfigId");
        }
        scmConfigService.requireById(scmConfigId);
        return scmConfigId;
    }

    private String normalizeCategory(String category) {
        if (!hasText(category)) {
            return null;
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeScope(String scope) {
        if (!hasText(scope)) {
            throw new BizException(ResultCode.PARAM_ERROR, "Prompt 模板作用域不能为空");
        }
        return scope.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeTemplateCode(String templateCode) {
        if (!hasText(templateCode)) {
            throw new BizException(ResultCode.PARAM_ERROR, "Prompt 模板编码不能为空");
        }
        return templateCode.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        if (!hasText(status)) {
            return STATUS_ACTIVE;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_DISABLED.equals(normalized)) {
            throw new BizException(ResultCode.PARAM_ERROR, "Prompt 模板状态不合法: " + status);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
