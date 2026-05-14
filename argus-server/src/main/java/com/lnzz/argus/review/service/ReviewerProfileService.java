package com.lnzz.argus.review.service;

import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.entity.ReviewerProfile;

import java.util.List;
import java.util.Map;

/**
 * 提交者画像服务。
 *
 * @author lnzz
 * @since 1.0.0
 */
public interface ReviewerProfileService {

    /**
     * 更新提交者画像。
     *
     * @param task 评审任务
     * @param score 评分结果
     */
    void updateProfile(ReviewTask task, ScoreCalculator.ScoreResult score);

    /**
     * 查询个人画像。
     *
     * @param authorName 作者名
     * @param scmProvider SCM 平台
     * @return 画像
     */
    ReviewerProfile getProfile(String authorName, String scmProvider);

    /**
     * 查询团队画像列表。
     *
     * @param scmProvider SCM 平台
     * @return 画像列表
     */
    List<ReviewerProfile> listTeamProfiles(String scmProvider);

    /**
     * 查询质量趋势。
     *
     * @param authorName 作者名
     * @param scmProvider SCM 平台
     * @return 趋势数据
     */
    List<Integer> getTrend(String authorName, String scmProvider);

    /**
     * 查询团队高频问题。
     *
     * @param scmProvider SCM 平台
     * @param days 统计天数
     * @return 高频问题
     */
    List<Map<String, Object>> getTopIssues(String scmProvider, int days);
}
