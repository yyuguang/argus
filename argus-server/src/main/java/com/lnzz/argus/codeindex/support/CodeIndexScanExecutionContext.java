package com.lnzz.argus.codeindex.support;

import com.lnzz.argus.scm.model.DiffFile;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @classname: CodeIndexScanExecutionContext
 * @author: Fantasy
 * @date: 2026/05/25 09:35
 * @description: 源码索引扫描执行上下文，封装异步任务 ID、进度回调和 Diff 输入，避免扫描入口参数膨胀。
 */
@Data
public class CodeIndexScanExecutionContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 扫描任务 ID。
     */
    private Long taskId;

    /**
     * 进度回调。
     */
    private transient CodeIndexScanProgressCallback progressCallback;

    /**
     * Diff 文件列表。
     */
    private List<DiffFile> diffFiles = new ArrayList<>();

    /**
     * 构建空执行上下文。
     *
     * @return 空执行上下文
     */
    public static CodeIndexScanExecutionContext empty() {
        return new CodeIndexScanExecutionContext();
    }

    /**
     * 构建仅包含进度回调的执行上下文。
     *
     * @param taskId 扫描任务 ID
     * @param progressCallback 进度回调
     * @return 扫描执行上下文
     */
    public static CodeIndexScanExecutionContext progress(Long taskId,
                                                         CodeIndexScanProgressCallback progressCallback) {
        CodeIndexScanExecutionContext context = new CodeIndexScanExecutionContext();
        context.setTaskId(taskId);
        context.setProgressCallback(progressCallback);
        return context;
    }

    /**
     * 构建包含 Diff 文件的执行上下文。
     *
     * @param diffFiles Diff 文件列表
     * @return 扫描执行上下文
     */
    public static CodeIndexScanExecutionContext diffFiles(List<DiffFile> diffFiles) {
        CodeIndexScanExecutionContext context = new CodeIndexScanExecutionContext();
        context.setDiffFiles(diffFiles == null ? new ArrayList<>() : new ArrayList<>(diffFiles));
        return context;
    }

    /**
     * 构建包含 Diff 文件和进度回调的执行上下文。
     *
     * @param diffFiles Diff 文件列表
     * @param taskId 扫描任务 ID
     * @param progressCallback 进度回调
     * @return 扫描执行上下文
     */
    public static CodeIndexScanExecutionContext diffFilesWithProgress(List<DiffFile> diffFiles, Long taskId,
                                                                      CodeIndexScanProgressCallback progressCallback) {
        CodeIndexScanExecutionContext context = diffFiles(diffFiles);
        context.setTaskId(taskId);
        context.setProgressCallback(progressCallback);
        return context;
    }
}
