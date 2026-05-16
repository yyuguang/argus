package com.lnzz.argus.error.service;

import com.lnzz.argus.error.entity.ErrorTypeRule;

import java.util.List;
import java.util.Map;

/**
 * 错误类型识别规则服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ErrorTypeRuleService {

    List<ErrorTypeRule> list(String errorType, Boolean enabled, String keyword);

    List<Map<String, String>> listTypes();

    ErrorTypeRule create(ErrorTypeRule request);

    ErrorTypeRule update(Long id, ErrorTypeRule request);

    void delete(Long id);
}
