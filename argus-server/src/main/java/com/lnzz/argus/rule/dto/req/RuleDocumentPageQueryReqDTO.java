package com.lnzz.argus.rule.dto.req;

import com.lnzz.argus.common.request.BasePageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @classname: RuleDocumentPageQueryReqDTO
 * @author: Fantasy
 * @date: 2026/05/17 22:58
 * @description: 规则文档分页查询请求，承载规则管理页列表筛选条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RuleDocumentPageQueryReqDTO extends BasePageRequest {

    /**
     * 规范分类。
     */
    private String category;

    /**
     * 作用域：GLOBAL/SCM。
     */
    private String scope;

    /**
     * SCM 仓库配置 ID。
     */
    private Long scmConfigId;

    /**
     * 文档状态：DRAFT/ACTIVE/DISABLED/ARCHIVED。
     */
    private String status;

    /**
     * 解析状态：PENDING/SUCCESS/FAILED。
     */
    private String parseStatus;

    /**
     * 向量化状态：PENDING/SUCCESS/FAILED。
     */
    private String vectorStatus;

    /**
     * 关键字，匹配文档名称、文档编码或文件名。
     */
    private String keyword;
}
