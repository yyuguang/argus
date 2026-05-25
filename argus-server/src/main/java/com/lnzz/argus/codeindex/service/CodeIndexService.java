package com.lnzz.argus.codeindex.service;

import com.lnzz.argus.codeindex.dto.req.CodeClassPageReqDTO;
import com.lnzz.argus.codeindex.dto.req.CodeIndexPageReqDTO;
import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeClassIndexResDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexDetailResDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.scanner.RepositoryCodeIndexDraft;
import com.lnzz.argus.common.result.PageResult;
import com.lnzz.argus.scm.entity.ScmConfig;

/**
 * @classname: CodeIndexService
 * @author: Fantasy
 * @date: 2026/05/19 17:05
 * @description: 源码索引服务接口，定义索引查询、扫描结果持久化和类型索引查询能力。
 */
public interface CodeIndexService {

    /**
     * 分页查询源码索引摘要。
     *
     * @param requestDTO 查询请求
     * @return 索引摘要分页结果
     */
    PageResult<CodeIndexSummaryResDTO> pageIndexes(CodeIndexPageReqDTO requestDTO);

    /**
     * 查询源码索引详情。
     *
     * @param indexId 源码索引 ID
     * @return 索引详情
     */
    CodeIndexDetailResDTO getIndexDetail(Long indexId);

    /**
     * 查询仓库指定分支最近一次成功索引。
     *
     * @param scmConfigId SCM 仓库配置 ID
     * @param branchName 分支名称
     * @return 最近一次成功索引摘要
     */
    CodeIndexSummaryResDTO getLatestSuccessfulIndex(Long scmConfigId, String branchName);

    /**
     * 查询仓库指定提交号的成功索引。
     *
     * @param scmConfigId SCM 仓库配置 ID
     * @param commitSha 提交号
     * @return 成功索引摘要，未命中或非成功状态返回 null
     */
    CodeIndexSummaryResDTO getSuccessfulIndexByCommit(Long scmConfigId, String commitSha);

    /**
     * 保存成功扫描产生的源码索引快照。
     *
     * @param scmConfig SCM 仓库配置
     * @param requestDTO 扫描请求
     * @param draft 源码索引草稿
     * @return 保存后的索引摘要
     */
    CodeIndexSummaryResDTO saveSuccessfulIndex(ScmConfig scmConfig,
                                               CodeIndexScanReqDTO requestDTO,
                                               RepositoryCodeIndexDraft draft);

    /**
     * 记录扫描失败快照。
     *
     * @param scmConfig SCM 仓库配置
     * @param requestDTO 扫描请求
     * @param errorMessage 失败原因
     * @return 失败索引摘要
     */
    CodeIndexSummaryResDTO markScanFailed(ScmConfig scmConfig,
                                          CodeIndexScanReqDTO requestDTO,
                                          String errorMessage);

    /**
     * 分页查询索引内 Java 类型。
     *
     * @param indexId 源码索引 ID
     * @param requestDTO 查询请求
     * @return Java 类型索引分页结果
     */
    PageResult<CodeClassIndexResDTO> pageClasses(Long indexId, CodeClassPageReqDTO requestDTO);
}
