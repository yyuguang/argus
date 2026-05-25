package com.lnzz.argus.codeindex.service;

import com.lnzz.argus.codeindex.dto.req.CodeIndexScanTaskCreateReqDTO;
import com.lnzz.argus.codeindex.dto.res.CodeIndexScanTaskResDTO;

/**
 * @classname: CodeIndexScanTaskService
 * @author: Fantasy
 * @date: 2026/05/25 08:50
 * @description: 源码索引扫描任务服务接口，定义任务创建、查询和状态流转能力。
 */
public interface CodeIndexScanTaskService {

    /**
     * 创建源码索引扫描任务。
     *
     * @param scmConfigId SCM 配置 ID
     * @param requestDTO 创建任务请求
     * @return 扫描任务响应
     * @author Fantasy
     * @date 2026/05/25 08:50
     */
    CodeIndexScanTaskResDTO createTask(Long scmConfigId, CodeIndexScanTaskCreateReqDTO requestDTO);

    /**
     * 按任务 ID 查询扫描任务。
     *
     * @param taskId 扫描任务 ID
     * @return 扫描任务响应；不存在或已删除时返回 null
     * @author Fantasy
     * @date 2026/05/25 08:50
     */
    CodeIndexScanTaskResDTO getTask(Long taskId);

    /**
     * 查询指定仓库和分支下最近的运行中扫描任务。
     *
     * @param scmConfigId SCM 配置 ID
     * @param branchName 分支名称
     * @return 运行中扫描任务；不存在时返回 null
     * @author Fantasy
     * @date 2026/05/25 08:50
     */
    CodeIndexScanTaskResDTO findRunningTask(Long scmConfigId, String branchName);

    /**
     * 标记任务进入运行中状态。
     *
     * @param taskId 扫描任务 ID
     * @author Fantasy
     * @date 2026/05/25 08:50
     */
    void markRunning(Long taskId);

    /**
     * 标记任务成功。
     *
     * @param taskId 扫描任务 ID
     * @param resultIndexId 成功后关联的源码索引 ID
     * @author Fantasy
     * @date 2026/05/25 08:50
     */
    void markSuccess(Long taskId, Long resultIndexId);

    /**
     * 标记任务失败。
     *
     * @param taskId 扫描任务 ID
     * @param errorMessage 失败原因
     * @author Fantasy
     * @date 2026/05/25 08:50
     */
    void markFailed(Long taskId, String errorMessage);
}
