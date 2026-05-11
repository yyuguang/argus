package com.lnzz.argus.review.controller;

import com.lnzz.argus.common.result.Result;
import com.lnzz.argus.review.entity.ReviewerProfile;
import com.lnzz.argus.review.service.ReviewerProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@DisplayName("ReviewerProfileController - 查询接口")
class ReviewerProfileControllerTest {

    @Test
    @DisplayName("四个画像端点返回服务结果")
    void controllerEndpointsReturnServiceData() {
        ReviewerProfileService service = mock(ReviewerProfileService.class);
        ReviewerProfileController controller = new ReviewerProfileController(service);

        ReviewerProfile profile = new ReviewerProfile();
        profile.setAuthorName("zhangsan");
        when(service.getProfile("zhangsan", "github")).thenReturn(profile);
        when(service.listTeamProfiles("github")).thenReturn(List.of(profile));
        when(service.getTrend("zhangsan", "github")).thenReturn(List.of(70, 80));
        when(service.getTopIssues("github", 30)).thenReturn(List.of(Map.of("rule", "NULL_CHECK", "count", 3)));

        Result<ReviewerProfile> profileResult = controller.getProfile("zhangsan", "github");
        Result<List<ReviewerProfile>> listResult = controller.listProfiles("github");
        Result<List<Integer>> trendResult = controller.getTrend("zhangsan", "github");
        Result<List<Map<String, Object>>> issuesResult = controller.getTopIssues("github", 30);

        assertEquals("zhangsan", profileResult.getData().getAuthorName());
        assertEquals(1, listResult.getData().size());
        assertEquals(List.of(70, 80), trendResult.getData());
        assertEquals("NULL_CHECK", issuesResult.getData().get(0).get("rule"));
    }
}
