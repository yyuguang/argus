package com.lnzz.argus.codeindex.service;

/**
 * @classname: CodeIndexScanTaskExecutor
 * @author: Fantasy
 * @date: 2026/05/25 10:50
 * @description: 源码索引扫描任务执行器接口，定义扫描任务后台提交能力。
 */
public interface CodeIndexScanTaskExecutor {

    /**
     * 提交源码索引扫描任务。
     *
     * @param taskId 扫描任务 ID
     * @return true 表示任务已提交执行；false 表示任务不可执行或提交失败
     * @author Fantasy
     * @date 2026/05/25 10:50
     */
    boolean submit(Long taskId);
}
