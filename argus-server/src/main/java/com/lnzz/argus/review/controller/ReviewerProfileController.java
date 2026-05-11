package com.lnzz.argus.review.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.review.entity.ReviewerProfile;
import com.lnzz.argus.review.service.ReviewerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 提交者画像查询 API。
 *
 * @author lnzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/review/profiles")
@RequiredArgsConstructor
public class ReviewerProfileController {

    private final ReviewerProfileService reviewerProfileService;

    /**
     * 查询个人画像详情。
     *
     * @param authorName 作者名
     * @param scmProvider SCM 平台
     * @return 个人画像
     */
    @GetMapping("/{authorName}")
    public Result<ReviewerProfile> getProfile(@PathVariable String authorName,
                                              @RequestParam String scmProvider) {
        return Result.success(reviewerProfileService.getProfile(authorName, scmProvider));
    }

    /**
     * 查询团队画像列表。
     *
     * @param scmProvider SCM 平台
     * @return 团队画像
     */
    @GetMapping
    public Result<List<ReviewerProfile>> listProfiles(@RequestParam String scmProvider) {
        return Result.success(reviewerProfileService.listTeamProfiles(scmProvider));
    }

    /**
     * 查询个人质量趋势。
     *
     * @param authorName 作者名
     * @param scmProvider SCM 平台
     * @return 趋势数据
     */
    @GetMapping("/{authorName}/trend")
    public Result<List<Integer>> getTrend(@PathVariable String authorName,
                                          @RequestParam String scmProvider) {
        return Result.success(reviewerProfileService.getTrend(authorName, scmProvider));
    }

    /**
     * 查询团队高频问题。
     *
     * @param scmProvider SCM 平台
     * @param days 统计天数
     * @return 高频问题
     */
    @GetMapping("/top-issues")
    public Result<List<Map<String, Object>>> getTopIssues(@RequestParam String scmProvider,
                                                          @RequestParam(defaultValue = "30") int days) {
        return Result.success(reviewerProfileService.getTopIssues(scmProvider, days));
    }
}
