package com.lnzz.argus.codeindex.service;

import com.lnzz.argus.codeindex.dto.req.CodeIndexScanReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexSummaryResDTO;
import com.lnzz.argus.codeindex.support.CodeIndexScanExecutionContext;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;

import java.util.List;

/**
 * @classname: CodeIndexScanService
 * @author: Fantasy
 * @date: 2026/05/19 17:40
 * @description: 源码索引扫描编排服务接口，负责从 SCM 文件输入生成并持久化源码索引快照。
 */
public interface CodeIndexScanService {

    /**
     * 执行全量源码索引扫描。
     *
     * @param scmConfig SCM 仓库配置
     * @param requestDTO 扫描请求
     * @return 源码索引摘要
     */
    CodeIndexSummaryResDTO scanFull(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO);

    /**
     * 执行带进度回调的全量源码索引扫描。
     *
     * @param scmConfig SCM 仓库配置
     * @param requestDTO 扫描请求
     * @param executionContext 扫描执行上下文
     * @return 源码索引摘要
     */
    CodeIndexSummaryResDTO scanFull(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO,
                                    CodeIndexScanExecutionContext executionContext);

    /**
     * 执行增量源码索引扫描。
     *
     * @param scmConfig SCM 仓库配置
     * @param requestDTO 扫描请求
     * @param diffFiles Diff 文件列表
     * @return 源码索引摘要
     */
    CodeIndexSummaryResDTO scanIncremental(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO, List<DiffFile> diffFiles);

    /**
     * 执行带进度回调的增量源码索引扫描。
     *
     * @param scmConfig SCM 仓库配置
     * @param requestDTO 扫描请求
     * @param executionContext 扫描执行上下文
     * @return 源码索引摘要
     */
    CodeIndexSummaryResDTO scanIncremental(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO,
                                           CodeIndexScanExecutionContext executionContext);

    /**
     * 基于请求中的已知文件路径执行源码索引扫描。
     *
     * @param scmConfig SCM 仓库配置
     * @param requestDTO 扫描请求
     * @return 源码索引摘要
     */
    CodeIndexSummaryResDTO scanKnownFiles(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO);

    /**
     * 基于请求中的已知文件路径执行带进度回调的源码索引扫描。
     *
     * @param scmConfig SCM 仓库配置
     * @param requestDTO 扫描请求
     * @param executionContext 扫描执行上下文
     * @return 源码索引摘要
     */
    CodeIndexSummaryResDTO scanKnownFiles(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO,
                                          CodeIndexScanExecutionContext executionContext);

    /**
     * 基于 Diff 文件列表执行增量扫描输入适配。
     *
     * @param scmConfig SCM 仓库配置
     * @param requestDTO 扫描请求
     * @param diffFiles Diff 文件列表
     * @return 源码索引摘要
     */
    CodeIndexSummaryResDTO scanDiffFiles(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO, List<DiffFile> diffFiles);

    /**
     * 基于 Diff 文件列表执行带进度回调的增量扫描输入适配。
     *
     * @param scmConfig SCM 仓库配置
     * @param requestDTO 扫描请求
     * @param executionContext 扫描执行上下文
     * @return 源码索引摘要
     */
    CodeIndexSummaryResDTO scanDiffFiles(ScmConfig scmConfig, CodeIndexScanReqDTO requestDTO,
                                         CodeIndexScanExecutionContext executionContext);
}
