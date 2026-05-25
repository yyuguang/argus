package com.lnzz.argus.review.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.review.entity.ReviewTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评审任务 Mapper
 *
 * @author lnzz
 * @since 1.0.0
 */
@Mapper
public interface ReviewTaskMapper extends BaseMapper<ReviewTask> {

    /**
     * 按主键查询评审任务。
     *
     * @param taskId 评审任务 ID
     * @return 评审任务
     */
    default ReviewTask findById(Long taskId) {
        return selectById(taskId);
    }

    /**
     * 分页查询评审任务。
     *
     * @param pageNo      页码
     * @param pageSize    每页数量
     * @param scmProvider SCM 平台
     * @param status      任务状态
     * @param keyword     关键词
     * @return 评审任务分页
     */
    default Page<ReviewTask> queryTasks(long pageNo, long pageSize, String scmProvider, String status, String keyword) {
        LambdaQueryWrapper<ReviewTask> wrapper = new LambdaQueryWrapper<ReviewTask>()
                .orderByDesc(ReviewTask::getUpdateTime)
                .orderByDesc(ReviewTask::getCreateTime);

        if (hasText(scmProvider)) {
            wrapper.eq(ReviewTask::getScmProvider, scmProvider.trim().toLowerCase());
        }
        if (hasText(status)) {
            wrapper.eq(ReviewTask::getStatus, status.trim().toUpperCase());
        }
        if (hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(query -> query.like(ReviewTask::getProjectName, value)
                    .or().like(ReviewTask::getRepoName, value)
                    .or().like(ReviewTask::getMrTitle, value)
                    .or().like(ReviewTask::getAuthorName, value));
        }

        return selectPage(new Page<>(pageNo, pageSize), wrapper);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
