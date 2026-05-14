package com.lnzz.argus.review.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.config.ReviewConfig;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.entity.ReviewerProfile;
import com.lnzz.argus.review.mapper.ReviewerProfileMapper;
import com.lnzz.argus.review.service.ReviewerProfileService;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 提交者画像服务实现。
 *
 * @author lnzz
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewerProfileServiceImpl implements ReviewerProfileService {

    private static final int TEAM_PROFILE_LIMIT = 50;

    private final ReviewerProfileMapper reviewerProfileMapper;
    private final VectorKnowledgeService vectorKnowledgeService;

    /**
     * 更新提交者画像。
     *
     * @param task 评审任务
     * @param score 评分结果
     */
    @Override
    public void updateProfile(ReviewTask task, ScoreCalculator.ScoreResult score) {
        if (task == null || score == null || task.getAuthorName() == null || task.getScmProvider() == null) {
            return;
        }

        String authorId = resolveAuthorId(task);
        ReviewerProfile profile = reviewerProfileMapper.selectByAuthorNameAndProvider(
                task.getAuthorName(), task.getScmProvider());
        boolean isNewProfile = profile == null;
        if (isNewProfile) {
            profile = new ReviewerProfile();
            profile.setAuthorName(task.getAuthorName());
            profile.setAuthorId(authorId);
            profile.setScmProvider(task.getScmProvider());
            profile.setTotalReviews(0);
            profile.setFirstReviewAt(resolveReviewTime(task));
        }

        int previousCount = defaultInt(profile.getTotalReviews());
        int newCount = previousCount + 1;
        profile.setTotalReviews(newCount);
        profile.setAvgScore(mergeAverage(profile.getAvgScore(), previousCount, score.getTotalScore()));
        profile.setDimensionStats(buildDimensionStats(profile.getDimensionStats(), previousCount, score));
        profile.setScoreTrend(updateScoreTrend(profile.getScoreTrend(), score.getTotalScore(),
                ReviewConfig.defaults().getProfile().getScoreTrendCount()));
        profile.setRecentReviews(updateRecentReviews(profile.getRecentReviews(), task, score,
                ReviewConfig.defaults().getProfile().getRecentReviewCount()));
        profile.setLastReviewAt(resolveReviewTime(task));
        profile.setTopIssueTags(buildTopIssueTags(authorId));
        profile.setTopIssueRules(buildTopIssueRules(authorId));

        if (isNewProfile) {
            reviewerProfileMapper.insert(profile);
        } else {
            reviewerProfileMapper.updateById(profile);
        }
    }

    /**
     * 查询个人画像。
     *
     * @param authorName 作者名
     * @param scmProvider SCM 平台
     * @return 画像
     */
    @Override
    public ReviewerProfile getProfile(String authorName, String scmProvider) {
        return reviewerProfileMapper.selectByAuthorNameAndProvider(authorName, scmProvider);
    }

    /**
     * 查询团队画像列表。
     *
     * @param scmProvider SCM 平台
     * @return 画像列表
     */
    @Override
    public List<ReviewerProfile> listTeamProfiles(String scmProvider) {
        return reviewerProfileMapper.selectTopByAvgScore(scmProvider, TEAM_PROFILE_LIMIT);
    }

    /**
     * 查询质量趋势。
     *
     * @param authorName 作者名
     * @param scmProvider SCM 平台
     * @return 趋势数据
     */
    @Override
    public List<Integer> getTrend(String authorName, String scmProvider) {
        ReviewerProfile profile = getProfile(authorName, scmProvider);
        return parseIntegerList(profile != null ? profile.getScoreTrend() : null);
    }

    /**
     * 查询团队高频问题。
     *
     * @param scmProvider SCM 平台
     * @param days 统计天数
     * @return 高频问题
     */
    @Override
    public List<Map<String, Object>> getTopIssues(String scmProvider, int days) {
        List<ReviewerProfile> profiles = reviewerProfileMapper.selectByScmProvider(scmProvider);
        Map<String, Integer> counter = new LinkedHashMap<>();
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(days, 1));

        for (ReviewerProfile profile : profiles) {
            if (profile.getLastReviewAt() != null && profile.getLastReviewAt().isBefore(threshold)) {
                continue;
            }
            for (Map<String, Object> ruleItem : parseObjectList(profile.getTopIssueRules())) {
                String rule = Objects.toString(ruleItem.get("rule"), null);
                int count = parseInt(ruleItem.get("count"));
                if (rule == null || rule.isBlank()) {
                    continue;
                }
                counter.merge(rule, count, Integer::sum);
            }
        }

        return counter.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(entry -> Map.<String, Object>of("rule", entry.getKey(), "count", entry.getValue()))
                .toList();
    }

    private BigDecimal mergeAverage(BigDecimal existingAvg, int previousCount, int currentScore) {
        BigDecimal total = (existingAvg != null ? existingAvg : BigDecimal.ZERO)
                .multiply(BigDecimal.valueOf(previousCount))
                .add(BigDecimal.valueOf(currentScore));
        return total.divide(BigDecimal.valueOf(previousCount + 1L), 2, RoundingMode.HALF_UP);
    }

    private String buildDimensionStats(String existingJson, int previousCount, ScoreCalculator.ScoreResult score) {
        Map<String, BigDecimal> current = new LinkedHashMap<>();
        Map<String, BigDecimal> previous = parseDecimalMap(existingJson);
        current.put("compliance", mergeAverage(previous.get("compliance"), previousCount, score.getComplianceScore()));
        current.put("correctness", mergeAverage(previous.get("correctness"), previousCount, score.getCorrectnessScore()));
        current.put("dataSafety", mergeAverage(previous.get("dataSafety"), previousCount, score.getDataSafetyScore()));
        current.put("performance", mergeAverage(previous.get("performance"), previousCount, score.getPerformanceScore()));
        current.put("maintainability", mergeAverage(previous.get("maintainability"), previousCount, score.getMaintainabilityScore()));
        return JSON.toJSONString(current);
    }

    private String updateScoreTrend(String existingJson, int newScore, int maxCount) {
        List<Integer> trend = parseIntegerList(existingJson);
        trend.add(newScore);
        trimHead(trend, maxCount);
        return JSON.toJSONString(trend);
    }

    private String updateRecentReviews(String existingJson, ReviewTask task,
                                       ScoreCalculator.ScoreResult score, int maxCount) {
        List<Map<String, Object>> recent = parseObjectList(existingJson);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", task.getId());
        item.put("score", score.getTotalScore());
        item.put("level", score.getScoreLevel());
        item.put("issues", score.getCriticalCount() + score.getMajorCount() + score.getMinorCount());
        item.put("reviewedAt", resolveReviewTime(task));
        recent.add(item);
        trimHead(recent, maxCount);
        return JSON.toJSONString(recent);
    }

    private String buildTopIssueTags(String authorId) {
        List<Document> docs = vectorKnowledgeService.getAuthorTopIssues(authorId,
                ReviewConfig.defaults().getProfile().getClusterTopk());
        List<String> tags = docs.stream()
                .map(doc -> Objects.toString(doc.getMetadata().get("rule"),
                        Objects.toString(doc.getMetadata().get("category"), "")))
                .filter(tag -> tag != null && !tag.isBlank())
                .distinct()
                .limit(5)
                .toList();
        return JSON.toJSONString(tags);
    }

    private String buildTopIssueRules(String authorId) {
        List<Document> docs = vectorKnowledgeService.getAuthorTopIssues(authorId,
                ReviewConfig.defaults().getProfile().getClusterTopk());
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (Document doc : docs) {
            String rule = Objects.toString(doc.getMetadata().get("rule"), null);
            if (rule == null || rule.isBlank()) {
                continue;
            }
            counter.merge(rule, 1, Integer::sum);
        }
        List<Map<String, Object>> topRules = counter.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> Map.<String, Object>of("rule", entry.getKey(), "count", entry.getValue()))
                .toList();
        return JSON.toJSONString(topRules);
    }

    private String resolveAuthorId(ReviewTask task) {
        if (task.getAuthorId() != null && !task.getAuthorId().isBlank()) {
            return task.getAuthorId();
        }
        return task.getScmProvider() + ":" + task.getAuthorName();
    }

    private LocalDateTime resolveReviewTime(ReviewTask task) {
        if (task.getUpdateTime() != null) {
            return task.getUpdateTime();
        }
        if (task.getCreateTime() != null) {
            return task.getCreateTime();
        }
        return LocalDateTime.now();
    }

    private Map<String, BigDecimal> parseDecimalMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return JSON.parseObject(json, new TypeReference<Map<String, BigDecimal>>() {});
    }

    private List<Integer> parseIntegerList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(JSON.parseObject(json, new TypeReference<List<Integer>>() {}));
    }

    private List<Map<String, Object>> parseObjectList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(JSON.parseObject(json, new TypeReference<List<Map<String, Object>>>() {}));
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int parseInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    private <T> void trimHead(List<T> items, int maxCount) {
        while (items.size() > maxCount) {
            items.remove(0);
        }
    }
}
