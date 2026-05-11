package com.lnzz.argus.review.service;

import com.alibaba.fastjson2.JSON;
import com.lnzz.argus.knowledge.vector.VectorKnowledgeService;
import com.lnzz.argus.review.ai.ScoreCalculator;
import com.lnzz.argus.review.entity.ReviewTask;
import com.lnzz.argus.review.entity.ReviewerProfile;
import com.lnzz.argus.review.mapper.ReviewerProfileMapper;
import com.lnzz.argus.review.service.impl.ReviewerProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewerProfileService - 画像更新与查询")
class ReviewerProfileServiceTest {

    @Mock
    private ReviewerProfileMapper reviewerProfileMapper;
    @Mock
    private VectorKnowledgeService vectorKnowledgeService;

    private ReviewerProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReviewerProfileServiceImpl(reviewerProfileMapper, vectorKnowledgeService);
    }

    @Test
    @DisplayName("updateProfile 新画像时插入并回写聚类结果")
    void updateProfileInsertsNewProfile() {
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore();

        when(reviewerProfileMapper.selectByAuthorNameAndProvider("zhangsan", "github")).thenReturn(null);
        when(vectorKnowledgeService.getAuthorTopIssues("github:zhangsan", 10))
                .thenReturn(List.of(
                        Document.builder().text("issue1").metadata(Map.of("rule", "NULL_CHECK", "category", "CORRECTNESS")).build(),
                        Document.builder().text("issue2").metadata(Map.of("rule", "NULL_CHECK", "category", "CORRECTNESS")).build(),
                        Document.builder().text("issue3").metadata(Map.of("rule", "TX_BOUNDARY", "category", "MAINTAINABILITY")).build()
                ));

        service.updateProfile(task, score);

        ArgumentCaptor<ReviewerProfile> captor = ArgumentCaptor.forClass(ReviewerProfile.class);
        verify(reviewerProfileMapper).insert(captor.capture());
        ReviewerProfile profile = captor.getValue();
        assertEquals("zhangsan", profile.getAuthorName());
        assertEquals("github:zhangsan", profile.getAuthorId());
        assertEquals(1, profile.getTotalReviews());
        assertEquals(new BigDecimal("82.00"), profile.getAvgScore());
        assertTrue(profile.getDimensionStats().contains("compliance"));
        assertTrue(profile.getTopIssueTags().contains("NULL_CHECK"));
        assertTrue(profile.getTopIssueRules().contains("TX_BOUNDARY"));
    }

    @Test
    @DisplayName("updateProfile 已有画像时增量更新平均分与趋势")
    void updateProfileUpdatesExistingProfile() {
        ReviewTask task = createTask();
        ScoreCalculator.ScoreResult score = createScore();
        ReviewerProfile existing = new ReviewerProfile();
        existing.setId(10L);
        existing.setAuthorName("zhangsan");
        existing.setAuthorId("github:zhangsan");
        existing.setScmProvider("github");
        existing.setTotalReviews(2);
        existing.setAvgScore(new BigDecimal("70.00"));
        existing.setDimensionStats("{\"compliance\":60,\"correctness\":70,\"dataSafety\":80,\"performance\":65,\"maintainability\":75}");
        existing.setScoreTrend("[70,72]");
        existing.setRecentReviews("[{\"taskId\":1,\"score\":70}]");
        existing.setFirstReviewAt(LocalDateTime.now().minusDays(5));

        when(reviewerProfileMapper.selectByAuthorNameAndProvider("zhangsan", "github")).thenReturn(existing);
        when(vectorKnowledgeService.getAuthorTopIssues("github:zhangsan", 10)).thenReturn(List.of());

        service.updateProfile(task, score);

        ArgumentCaptor<ReviewerProfile> captor = ArgumentCaptor.forClass(ReviewerProfile.class);
        verify(reviewerProfileMapper).updateById(captor.capture());
        ReviewerProfile updated = captor.getValue();
        assertEquals(3, updated.getTotalReviews());
        assertEquals(new BigDecimal("74.00"), updated.getAvgScore());
        assertTrue(updated.getScoreTrend().contains("82"));
        assertTrue(updated.getRecentReviews().contains("\"score\":82"));
    }

    @Test
    @DisplayName("getTopIssues 聚合团队规则计数")
    void getTopIssuesAggregatesRules() {
        ReviewerProfile p1 = new ReviewerProfile();
        p1.setTopIssueRules("[{\"rule\":\"NULL_CHECK\",\"count\":2},{\"rule\":\"SQL_SAFE\",\"count\":1}]");
        p1.setLastReviewAt(LocalDateTime.now());
        ReviewerProfile p2 = new ReviewerProfile();
        p2.setTopIssueRules("[{\"rule\":\"NULL_CHECK\",\"count\":1}]");
        p2.setLastReviewAt(LocalDateTime.now());

        when(reviewerProfileMapper.selectByScmProvider("github")).thenReturn(List.of(p1, p2));

        List<Map<String, Object>> issues = service.getTopIssues("github", 30);

        assertEquals("NULL_CHECK", issues.get(0).get("rule"));
        assertEquals(3, issues.get(0).get("count"));
    }

    private ReviewTask createTask() {
        ReviewTask task = new ReviewTask();
        task.setId(2L);
        task.setScmProvider("github");
        task.setProjectName("demo");
        task.setAuthorId("github:zhangsan");
        task.setAuthorName("zhangsan");
        task.setUpdateTime(LocalDateTime.now());
        return task;
    }

    private ScoreCalculator.ScoreResult createScore() {
        ScoreCalculator.ScoreResult score = new ScoreCalculator.ScoreResult();
        score.setTotalScore(82);
        score.setScoreLevel("B");
        score.setComplianceScore(80);
        score.setCorrectnessScore(84);
        score.setDataSafetyScore(86);
        score.setPerformanceScore(78);
        score.setMaintainabilityScore(82);
        score.setCriticalCount(0);
        score.setMajorCount(1);
        score.setMinorCount(2);
        return score;
    }
}
