package com.lnzz.argus.rule.service;

import com.lnzz.argus.rule.dao.entity.RuleDocumentChunk;

import java.util.List;

/**
 * @classname: RuleChunkService
 * @author: Fantasy
 * @date: 2026/05/17 23:16
 * @description: 规则文档分块服务接口，负责规则文本切分和分块重建。
 */
public interface RuleChunkService {

    /**
     * 查询指定规则文档下全部有效分块。
     *
     * @param documentId 规则文档 ID
     * @return 规则文档分块列表
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    List<RuleDocumentChunk> listChunks(Long documentId);

    /**
     * 按解析文本重建规则文档分块。
     *
     * @param documentId 规则文档 ID
     * @param plainText  解析后的纯文本
     * @param operator   当前操作者
     * @return 重建后的分块数量
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    int rebuildChunks(Long documentId, String plainText, String operator);
}
