package com.lnzz.argus.rule.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * @classname: RuleDocumentStatusUpdateReqDTO
 * @author: Fantasy
 * @date: 2026/05/17 22:58
 * @description: 规则文档状态操作请求，统一承载启用、停用和重建索引动作参数。
 */
@Data
public class RuleDocumentStatusUpdateReqDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 规则文档 ID。
     */
    @NotNull
    private Long id;

    /**
     * 操作类型：ACTIVATE/DISABLE/REINDEX。
     */
    @NotBlank
    private String action;

    /**
     * 当前操作者。
     */
    private String operator;

    /**
     * 操作说明。
     */
    private String comment;
}
