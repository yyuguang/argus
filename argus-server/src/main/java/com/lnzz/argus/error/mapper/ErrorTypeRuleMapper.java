package com.lnzz.argus.error.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.error.entity.ErrorTypeRule;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 错误类型识别规则 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ErrorTypeRuleMapper extends BaseMapper<ErrorTypeRule> {

    /**
     * 按主键查询错误类型规则。
     *
     * @param id 规则 ID
     * @return 错误类型规则
     */
    default ErrorTypeRule findById(Long id) {
        return selectById(id);
    }

    /**
     * 按前端筛选条件查询错误类型规则。
     *
     * @param errorType 规则目标错误类型
     * @param enabled   是否启用
     * @param keyword   规则名称、表达式或备注关键词
     * @return 匹配的规则列表
     */
    default List<ErrorTypeRule> queryRules(String errorType, Boolean enabled, String keyword) {
        return selectList(new LambdaQueryWrapper<ErrorTypeRule>()
                .eq(hasText(errorType), ErrorTypeRule::getErrorType, errorType)
                .eq(enabled != null, ErrorTypeRule::getEnabled, enabled)
                .and(hasText(keyword), wrapper -> wrapper
                        .like(ErrorTypeRule::getRuleName, keyword)
                        .or()
                        .like(ErrorTypeRule::getPattern, keyword)
                        .or()
                        .like(ErrorTypeRule::getRemark, keyword))
                .orderByAsc(ErrorTypeRule::getPriority)
                .orderByAsc(ErrorTypeRule::getId));
    }

    /**
     * 查询全部启用规则，按优先级排序。
     *
     * @return 启用的错误类型识别规则
     */
    default List<ErrorTypeRule> findEnabledRules() {
        return selectList(new LambdaQueryWrapper<ErrorTypeRule>()
                .eq(ErrorTypeRule::getEnabled, true)
                .orderByAsc(ErrorTypeRule::getPriority)
                .orderByAsc(ErrorTypeRule::getId));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
