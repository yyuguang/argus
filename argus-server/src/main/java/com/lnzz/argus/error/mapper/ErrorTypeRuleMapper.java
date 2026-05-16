package com.lnzz.argus.error.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lnzz.argus.error.entity.ErrorTypeRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 错误类型识别规则 Mapper。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ErrorTypeRuleMapper extends BaseMapper<ErrorTypeRule> {
}
