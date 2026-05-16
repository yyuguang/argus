package com.lnzz.argus.error.service.impl;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.error.entity.ErrorTypeRule;
import com.lnzz.argus.error.mapper.ErrorTypeRuleMapper;
import com.lnzz.argus.error.parse.ErrorTypeIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ErrorTypeRuleServiceImpl - 错误类型规则配置")
class ErrorTypeRuleServiceImplTest {

    @Mock
    private ErrorTypeRuleMapper mapper;
    @Mock
    private ErrorTypeIdentifier identifier;

    @Test
    @DisplayName("创建规则时归一化字段并刷新识别缓存")
    void createNormalizesAndInvalidatesCache() {
        ErrorTypeRuleServiceImpl service = new ErrorTypeRuleServiceImpl(mapper, identifier);

        ErrorTypeRule request = new ErrorTypeRule();
        request.setRuleName("  Spring 404  ");
        request.setErrorType("http_error");
        request.setMatchField("exception_class");
        request.setMatchMode("exact");
        request.setPattern("NoResourceFoundException");

        service.create(request);

        ArgumentCaptor<ErrorTypeRule> captor = ArgumentCaptor.forClass(ErrorTypeRule.class);
        verify(mapper).insert(captor.capture());
        ErrorTypeRule saved = captor.getValue();
        assertEquals("Spring 404", saved.getRuleName());
        assertEquals("HTTP_ERROR", saved.getErrorType());
        assertEquals("EXCEPTION_CLASS", saved.getMatchField());
        assertEquals("EXACT", saved.getMatchMode());
        assertEquals(100, saved.getPriority());
        assertEquals(true, saved.getEnabled());
        verify(identifier).invalidateRuleCache();
    }

    @Test
    @DisplayName("非法正则表达式返回参数错误")
    void invalidRegexRejected() {
        ErrorTypeRuleServiceImpl service = new ErrorTypeRuleServiceImpl(mapper, identifier);

        ErrorTypeRule request = new ErrorTypeRule();
        request.setRuleName("bad regex");
        request.setErrorType("HTTP_ERROR");
        request.setMatchField("EXCEPTION_CLASS");
        request.setMatchMode("REGEX");
        request.setPattern("(");

        assertThrows(BizException.class, () -> service.create(request));
    }
}
