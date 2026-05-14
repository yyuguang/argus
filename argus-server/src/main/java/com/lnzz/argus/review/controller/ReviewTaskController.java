package com.lnzz.argus.review.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.service.ReviewTaskDetailService;
import com.lnzz.argus.review.service.ReviewTaskQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public Result<Map<String, Object>> listTasks(
            @RequestParam(defaultValue = "1") long pageNo,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String scmProvider,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {

        Page<ReviewTask> page = reviewTaskQueryService.queryTasks(pageNo, pageSize, scmProvider, status, keyword);
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
