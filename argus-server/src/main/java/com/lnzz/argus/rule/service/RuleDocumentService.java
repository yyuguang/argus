package com.lnzz.argus.rule.service;

import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.rule.dao.entity.RuleDocument;
import com.lnzz.argus.rule.dto.req.RuleDocumentPageQueryReqDTO;
import com.lnzz.argus.rule.dto.req.RuleDocumentStatusUpdateReqDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentDetailResDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentPageItemResDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentPreviewResDTO;

/**
 * @classname: RuleDocumentService
 * @author: Fantasy
 * @date: 2026/05/17 23:16
 * @description: 规则文档服务接口，负责规则文档列表、详情、预览和状态流转能力。
 */
public interface RuleDocumentService {

    /**
     * 分页查询规则文档列表。
     *
     * @param requestDTO 分页与筛选请求
     * @return 规则文档分页结果
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    PageResult<RuleDocumentPageItemResDTO> pageDocuments(RuleDocumentPageQueryReqDTO requestDTO);

    /**
     * 查询规则文档详情。
     *
     * @param documentId 规则文档 ID
     * @return 规则文档详情
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    RuleDocumentDetailResDTO getDocumentDetail(Long documentId);

    /**
     * 查询规则文档预览。
     *
     * @param documentId 规则文档 ID
     * @return 规则文档预览
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    RuleDocumentPreviewResDTO getDocumentPreview(Long documentId);

    /**
     * 更新规则文档状态或触发重建动作。
     *
     * @param requestDTO 状态操作请求
     * @return 更新后的规则文档详情
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    RuleDocumentDetailResDTO updateDocumentStatus(RuleDocumentStatusUpdateReqDTO requestDTO);

    /**
     * 根据 ID 查询规则文档实体，不存在时抛出业务异常。
     *
     * @param documentId 规则文档 ID
     * @return 规则文档实体
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    RuleDocument requireDocument(Long documentId);
}
