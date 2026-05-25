package com.lnzz.argus.review.service;

import com.lnzz.argus.common.exception.BizException;
import com.lnzz.argus.common.result.ResultCode;
import com.lnzz.argus.review.entity.ReviewIssue;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.mapper.ReviewIssueMapper;
import com.lnzz.argus.review.mapper.ReviewTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评审任务详情服务
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewTaskDetailService {

    private final ReviewTaskMapper reviewTaskMapper;
    private final ReviewIssueMapper reviewIssueMapper;

    public ReviewTaskDetail queryDetail(Long taskId) {
        ReviewTask task = reviewTaskMapper.findById(taskId);
        if (task == null) {
            log.warn("评审任务详情不存在: taskId={}", taskId);
            throw new BizException(ResultCode.NOT_FOUND, "评审任务不存在: " + taskId);
        }

        List<ReviewIssue> issues = reviewIssueMapper.findByTaskId(taskId);
        log.debug("查询评审任务详情: taskId={}, issueCount={}", taskId, issues.size());

        return new ReviewTaskDetail(task, issues);
    }

    public record ReviewTaskDetail(ReviewTask task, List<ReviewIssue> issues) {
    }
}
