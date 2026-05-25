package com.lnzz.argus.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.model.ReviewTaskPageRequest;
import com.lnzz.argus.review.service.ReviewTaskDetailService;
import com.lnzz.argus.review.service.ReviewTaskQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 评审任务查询 API
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/review/tasks")
@RequiredArgsConstructor
public class ReviewTaskController {

    private final ReviewTaskQueryService reviewTaskQueryService;
    private final ReviewTaskDetailService reviewTaskDetailService;

    /**
     * 分页查询评审任务。
     *
     * @param request 评审任务分页查询请求
     * @return 评审任务分页结果
     */
    @PostMapping("/page")
    public Result<Map<String, Object>> listTasks(@RequestBody(required = false) ReviewTaskPageRequest request) {
        ReviewTaskPageRequest safeRequest = request == null ? new ReviewTaskPageRequest() : request;

        Page<ReviewTask> page = reviewTaskQueryService.queryTasks(
                safeRequest.normalizedPageNo(),
                safeRequest.normalizedPageSize(),
                safeRequest.getScmProvider(),
                safeRequest.getStatus(),
                safeRequest.getKeyword());
        return Result.success(Map.of(
                "records", page.getRecords(),
                "total", page.getTotal(),
                "pageNo", page.getCurrent(),
                "pageSize", page.getSize()
        ));
    }

    @GetMapping("/{taskId}")
    public Result<ReviewTaskDetailService.ReviewTaskDetail> getTaskDetail(@PathVariable Long taskId) {
        return Result.success(reviewTaskDetailService.queryDetail(taskId));
    }
}
