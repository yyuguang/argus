package com.lnzz.argus.scm.service;

import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.scm.entity.ScmConfig;
import com.lnzz.argus.scm.model.DiffFile;
import com.lnzz.argus.scm.model.PullRequestEvent;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * SCM 平台服务抽象
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ScmPlatformService {

    /**
     * 平台标识
     */
    String getProvider();

    /**
     * 解析 Webhook 事件
     */
    PullRequestEvent parseWebhookEvent(Map<String, String> headers, String payload);

    /**
     * 校验 Webhook 签名
     */
    boolean verifyWebhookSignature(ScmConfig config, Map<String, String> headers, String payload);

    /**
     * 获取 PR/MR Diff
     */
    List<DiffFile> getPullRequestDiffs(ScmConfig config, ReviewTask task);

    /**
     * 获取指定 ref 的文件内容
     */
    String getFileContent(ScmConfig config, ReviewTask task, String filePath, String ref);

    /**
     * 获取指定 ref 的文件内容（无 ReviewTask，用于错误源码定位场景）
     */
    String getFileContent(ScmConfig config, String filePath, String ref);

    /**
     * 获取指定 ref 的仓库文件路径列表。
     */
    default List<String> listRepositoryFiles(ScmConfig config, String ref) {
        return List.of();
    }

    /**
     * 将指定 ref 的仓库快照按过滤条件物化到本地目录。
     *
     * @param config SCM 仓库配置
     * @param ref 分支或提交号
     * @param fileFilter 文件路径过滤器
     * @param repositoryRoot 本地临时仓库根目录
     * @param loadedFilePaths 已成功物化的文件路径集合
     * @param failedFilePaths 物化失败的文件路径集合
     * @param warnings 物化过程告警集合
     * @return 当前平台是否支持并已尝试快照物化
     */
    default boolean materializeRepositoryFiles(ScmConfig config,
                                               String ref,
                                               Predicate<String> fileFilter,
                                               Path repositoryRoot,
                                               Collection<String> loadedFilePaths,
                                               Collection<String> failedFilePaths,
                                               Collection<String> warnings) {
        return false;
    }

    /**
     * 添加 PR/MR 评论
     */
    Long addPullRequestComment(ScmConfig config, ReviewTask task, String body);

    /**
     * 设置 PR/MR 标签
     */
    void setPullRequestLabels(ScmConfig config, ReviewTask task, List<String> labels);
}
