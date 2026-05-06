package com.lnzz.argus.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.mapper.ReviewTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 评审任务查询服务
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ReviewTaskQueryService {

    private final ReviewTaskMapper reviewTaskMapper;

    public Page<ReviewTask> queryTasks(long pageNo, long pageSize, String scmProvider, String status, String keyword) {
        LambdaQueryWrapper<ReviewTask> wrapper = new LambdaQueryWrapper<ReviewTask>()
                .orderByDesc(ReviewTask::getUpdateTime)
                .orderByDesc(ReviewTask::getCreateTime);

        if (StringUtils.hasText(scmProvider)) {
            wrapper.eq(ReviewTask::getScmProvider, scmProvider.trim().toLowerCase());
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(ReviewTask::getStatus, status.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            wrapper.and(w -> w.like(ReviewTask::getProjectName, value)
                    .or().like(ReviewTask::getRepoName, value)
                    .or().like(ReviewTask::getMrTitle, value)
                    .or().like(ReviewTask::getAuthorName, value));
        }

        return reviewTaskMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
    }
}
