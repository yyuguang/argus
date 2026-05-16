package com.lnzz.argus.error.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lnzz.argus.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 错误类型识别规则。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("argus_error_type_rule")
public class ErrorTypeRule extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 规则名称 */
    private String ruleName;

    /** 识别后的标准错误类型 */
    private String errorType;

    /** 匹配字段：ANY/EXCEPTION_CLASS/CLASS_NAME/STACK_TRACE/MESSAGE/HTTP_STATUS */
    private String matchField;

    /** 匹配模式：EXACT/CONTAINS/REGEX/RANGE */
    private String matchMode;

    /** 匹配表达式 */
    private String pattern;

    /** 优先级，越小越先匹配 */
    private Integer priority;

    /** 是否启用 */
    private Boolean enabled;

    /** 是否内置初始化规则 */
    private Boolean builtin;

    /** 备注 */
    private String remark;
}
