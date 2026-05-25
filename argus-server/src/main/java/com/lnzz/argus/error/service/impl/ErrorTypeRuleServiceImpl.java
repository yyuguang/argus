package com.lnzz.argus.error.service.impl;

import com.lnzz.argus.common.constant.ErrorTypeRuleConstants;
import com.lnzz.argus.common.enums.ErrorType;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.error.entity.ErrorTypeRule;
import com.lnzz.argus.error.mapper.ErrorTypeRuleMapper;
import com.lnzz.argus.error.parse.ErrorTypeIdentifier;
import com.lnzz.argus.error.service.ErrorTypeRuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 错误类型识别规则服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorTypeRuleServiceImpl implements ErrorTypeRuleService {

    private final ErrorTypeRuleMapper mapper;
    private final ErrorTypeIdentifier errorTypeIdentifier;

    @Override
    public List<ErrorTypeRule> list(String errorType, Boolean enabled, String keyword) {
        String normalizedType = upper(trimToNull(errorType));
        String normalizedKeyword = trimToNull(keyword);
        List<ErrorTypeRule> rules = mapper.queryRules(normalizedType, enabled, normalizedKeyword);
        log.debug("查询错误类型规则: errorType={}, enabled={}, keyword={}, count={}",
                normalizedType, enabled, normalizedKeyword, rules.size());
        return rules;
    }

    @Override
    public List<Map<String, String>> listTypes() {
        return Arrays.stream(ErrorType.values())
                .map(type -> Map.of(
                        "value", type.name(),
                        "label", type.getDescription()))
                .toList();
    }

    @Override
    public ErrorTypeRule create(ErrorTypeRule request) {
        ErrorTypeRule rule = normalizeAndValidate(request);
        rule.setId(null);
        if (rule.getBuiltin() == null) {
            rule.setBuiltin(false);
        }
        mapper.insert(rule);
        errorTypeIdentifier.invalidateRuleCache();
        log.info("创建错误类型规则: ruleId={}, ruleName={}, errorType={}",
                rule.getId(), rule.getRuleName(), rule.getErrorType());
        return rule;
    }

    @Override
    public ErrorTypeRule update(Long id, ErrorTypeRule request) {
        requireById(id);
        ErrorTypeRule rule = normalizeAndValidate(request);
        rule.setId(id);
        mapper.updateById(rule);
        errorTypeIdentifier.invalidateRuleCache();
        ErrorTypeRule updated = mapper.findById(id);
        log.info("更新错误类型规则: ruleId={}, ruleName={}, errorType={}",
                id, rule.getRuleName(), rule.getErrorType());
        return updated != null ? updated : rule;
    }

    @Override
    public void delete(Long id) {
        requireById(id);
        mapper.deleteById(id);
        errorTypeIdentifier.invalidateRuleCache();
        log.info("删除错误类型规则: ruleId={}", id);
    }

    private ErrorTypeRule requireById(Long id) {
        ErrorTypeRule existing = mapper.findById(id);
        if (existing == null) {
            throw new BizException(ResultCode.NOT_FOUND, "错误类型规则不存在: " + id);
        }
        return existing;
    }

    private ErrorTypeRule normalizeAndValidate(ErrorTypeRule request) {
        if (request == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "错误类型规则不能为空");
        }
        request.setRuleName(trimToNull(request.getRuleName()));
        request.setErrorType(upper(trimToNull(request.getErrorType())));
        request.setMatchField(upper(trimToNull(request.getMatchField())));
        request.setMatchMode(upper(trimToNull(request.getMatchMode())));
        request.setPattern(trimToNull(request.getPattern()));
        request.setRemark(trimToNull(request.getRemark()));
        if (request.getPriority() == null) {
            request.setPriority(ErrorTypeRuleConstants.DEFAULT_PRIORITY);
        }
        if (request.getEnabled() == null) {
            request.setEnabled(true);
        }

        if (!StringUtils.hasText(request.getRuleName())) {
            throw new BizException(ResultCode.PARAM_ERROR, "规则名称不能为空");
        }
        if (!StringUtils.hasText(request.getErrorType()) || !isSupportedErrorType(request.getErrorType())) {
            throw new BizException(ResultCode.PARAM_ERROR, "错误类型不合法");
        }
        if (!ErrorTypeRuleConstants.SUPPORTED_MATCH_FIELDS.contains(request.getMatchField())) {
            throw new BizException(ResultCode.PARAM_ERROR, "匹配字段不合法");
        }
        if (!ErrorTypeRuleConstants.SUPPORTED_MATCH_MODES.contains(request.getMatchMode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "匹配模式不合法");
        }
        if (!StringUtils.hasText(request.getPattern())) {
            throw new BizException(ResultCode.PARAM_ERROR, "匹配表达式不能为空");
        }
        if (ErrorTypeRuleConstants.MATCH_MODE_REGEX.equals(request.getMatchMode())) {
            try {
                Pattern.compile(request.getPattern());
            } catch (PatternSyntaxException e) {
                throw new BizException(ResultCode.PARAM_ERROR, "正则表达式不合法: " + e.getDescription());
            }
        }
        if (ErrorTypeRuleConstants.MATCH_MODE_RANGE.equals(request.getMatchMode())
                && !ErrorTypeRuleConstants.MATCH_FIELD_HTTP_STATUS.equals(request.getMatchField())) {
            throw new BizException(ResultCode.PARAM_ERROR, "RANGE 模式仅支持 HTTP_STATUS 字段");
        }
        if (ErrorTypeRuleConstants.MATCH_FIELD_HTTP_STATUS.equals(request.getMatchField())
                && !ErrorTypeRuleConstants.MATCH_MODE_EXACT.equals(request.getMatchMode())
                && !ErrorTypeRuleConstants.MATCH_MODE_RANGE.equals(request.getMatchMode())) {
            throw new BizException(ResultCode.PARAM_ERROR, "HTTP_STATUS 仅支持 EXACT 或 RANGE");
        }
        return request;
    }

    private boolean isSupportedErrorType(String value) {
        return Arrays.stream(ErrorType.values()).anyMatch(type -> type.name().equals(value));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String upper(String value) {
        return value == null ? null : value.toUpperCase();
    }
}
