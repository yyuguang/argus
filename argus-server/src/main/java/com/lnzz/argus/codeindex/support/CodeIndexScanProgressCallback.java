package com.lnzz.argus.codeindex.support;

/**
 * @classname: CodeIndexScanProgressCallback
 * @author: Fantasy
 * @date: 2026/05/25 09:15
 * @description: 源码索引扫描进度回调契约，供扫描主链向扫描任务表上报阶段、计数器和终态。
 */
public interface CodeIndexScanProgressCallback {

    /**
     * 上报扫描阶段开始。
     *
     * @param taskId 扫描任务 ID
     * @param scanStage 扫描阶段
     * @param stageMessage 阶段说明
     * @author Fantasy
     * @date 2026/05/25 09:15
     */
    void onStageStart(Long taskId, String scanStage, String stageMessage);

    /**
     * 上报 SCM 文件读取数量。
     *
     * @param taskId 扫描任务 ID
     * @param loadedFileCount 已读取文件数量
     * @author Fantasy
     * @date 2026/05/25 09:15
     */
    void onFileLoaded(Long taskId, int loadedFileCount);

    /**
     * 上报 Java 文件解析进度。
     *
     * @param taskId 扫描任务 ID
     * @param parsedFileCount 已解析 Java 文件数量
     * @param totalJavaFileCount Java 文件总数
     * @param failedFileCount 解析失败文件数量
     * @author Fantasy
     * @date 2026/05/25 09:15
     */
    void onJavaParseProgress(Long taskId, int parsedFileCount, int totalJavaFileCount, int failedFileCount);

    /**
     * 上报索引持久化阶段统计。
     *
     * @param taskId 扫描任务 ID
     * @param classCount Java 类型数量
     * @param packageCount 包数量
     * @param warningCount 告警数量
     * @author Fantasy
     * @date 2026/05/25 09:15
     */
    void onPersisting(Long taskId, int classCount, int packageCount, int warningCount);

    /**
     * 上报扫描成功终态。
     *
     * @param taskId 扫描任务 ID
     * @param resultIndexId 成功索引 ID
     * @param classCount Java 类型数量
     * @param packageCount 包数量
     * @param warningCount 告警数量
     * @author Fantasy
     * @date 2026/05/25 09:15
     */
    void onSuccess(Long taskId, Long resultIndexId, int classCount, int packageCount, int warningCount);

    /**
     * 上报扫描失败终态。
     *
     * @param taskId 扫描任务 ID
     * @param errorMessage 失败原因
     * @author Fantasy
     * @date 2026/05/25 09:15
     */
    void onFailure(Long taskId, String errorMessage);
}
