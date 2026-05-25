package com.lnzz.argus.rule.service;

import com.lnzz.argus.rule.dto.req.RuleDocumentImportReqDTO;
import com.lnzz.argus.rule.dto.res.RuleDocumentDetailResDTO;

/**
 * @classname: RuleDocumentImportService
 * @author: Fantasy
 * @date: 2026/05/17 23:16
 * @description: 规则文档导入服务接口，负责文件解析、规则文档保存和分块触发。
 */
public interface RuleDocumentImportService {

    /**
     * 导入规则文档并触发解析、分块流程。
     *
     * @param fileBytes   上传文件字节数组
     * @param fileName    原始文件名
     * @param requestDTO  规则文档导入请求
     * @return 导入后的规则文档详情
     * @author Fantasy
     * @date 2026/05/17 23:16
     */
    RuleDocumentDetailResDTO importDocument(byte[] fileBytes, String fileName, RuleDocumentImportReqDTO requestDTO);
}
