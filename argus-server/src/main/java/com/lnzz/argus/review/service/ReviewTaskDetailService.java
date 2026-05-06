package com.lnzz.argus.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.review.entity.ReviewIssue;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.mapper.ReviewIssueMapper;
import com.lnzz.argus.review.mapper.ReviewTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评审任务详情服务
 *
 * @author lnzz
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ReviewTaskDetailService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewIssueMapper reviewIssueMapper;

    public ReviewTaskDetail queryDetail(Long taskId) {
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.NOT_FOUND, "评审任务不存在: " + taskId);
        }

        List<ReviewIssue> issues = reviewIssueMapper.selectList(new LambdaQueryWrapper<ReviewIssue>()
                .eq(ReviewIssue::getTaskId, taskId)
                .orderByAsc(ReviewIssue::getSeverity)
                .orderByAsc(ReviewIssue::getFilePath)
                .orderByAsc(ReviewIssue::getStartLine));

        return new ReviewTaskDetail(task, issues);
    }

    public record ReviewTaskDetail(ReviewTask task, List<ReviewIssue> issues) {
    }
}
