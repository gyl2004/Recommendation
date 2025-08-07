package com.recommendation.service.recall;

import com.recommendation.common.dto.RecommendRequest;
import com.recommendation.common.entity.Content;
import com.recommendation.common.entity.User;
import com.recommendation.common.entity.UserBehavior;
import com.recommendation.common.repository.ContentRepository;
import com.recommendation.common.repository.UserBehaviorRepository;
import com.recommendation.common.repository.UserRepository;
import com.recommendation.service.recall.dto.RecallResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 召回服务集成测试
 * 支持需求5.1, 5.2: 推荐算法引擎 - 多路召回算法集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RecallIntegrationTest {

    @Autowired
    private MultiPathRecallService multiPathRecallService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private UserBehaviorRepository userBehaviorRepository;

    private User testUser;
    private Content testContent1;
    private Content testContent2;
    private Content testContent3;

    @BeforeEach
    void setUp() {
        // 创建测试用户
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setStatus(1);
        testUser = userRepository.save(testUser);

        // 创建测试内容
        testContent1 = createTestContent("Test Article 1", Content.ContentType.ARTICLE, 1, Arrays.asList("tech", "java"));
        testContent2 = createTestContent("Test Article 2", Content.ContentType.ARTICLE, 1, Arrays.asList("tech", "spring"));
        testContent3 = createTestContent("Test Video 1", Content.ContentType.VIDEO, 2, Arrays.asList("tutorial", "java"));

        testContent1 = contentRepository.save(testContent1);
        testContent2 = contentRepository.save(testContent2);
        testContent3 = contentRepository.save(testContent3);

        // 创建用户行为数据
        createUserBehavior(testUser.getId(), testContent1.getId(), UserBehavior.ActionType.LIKE);
        createUserBehavior(testUser.getId(), testContent2.getId(), UserBehavior.ActionType.VIEW);
        createUserBehavior(testUser.getId(), testContent3.getId(), UserBehavior.ActionType.SHARE);
    }

    @Test
    void testMultiPathRecall_WithRealData_ShouldReturnResults() {
        // Given
        RecommendRequest request = RecommendRequest.builder()
            .userId(testUser.getId().toString())
            .size(10)
            .contentType("mixed")
            .build();

        // When
        List<RecallResult> results = multiPathRecallService.executeMultiPathRecall(request);

        // Then
        assertNotNull(results);
        assertFalse(results.isEmpty());

        // 验证每个召回结果
        for (RecallResult result : results) {
            assertNotNull(result.getAlgorithm());
            assertNotNull(result.getContents());
            assertTrue(result.getWeight() > 0);
            assertTrue(result.getDuration() >= 0);
        }
    }

    @Test
    void testMergeRecallResults_WithRealData_ShouldMergeCorrectly() {
        // Given
        RecommendRequest request = RecommendRequest.builder()
            .userId(testUser.getId().toString())
            .size(5)
            .contentType("mixed")
            .build();

        List<RecallResult> recallResults = multiPathRecallService.executeMultiPathRecall(request);

        // When
        List<Content> mergedContents = multiPathRecallService.mergeRecallResults(recallResults, 5);

        // Then
        assertNotNull(mergedContents);
        assertTrue(mergedContents.size() <= 5);

        // 验证内容都是已发布状态
        for (Content content : mergedContents) {
            assertTrue(content.isPublished());
        }
    }

    @Test
    void testMultiPathRecall_WithContentTypeFilter_ShouldFilterCorrectly() {
        // Given
        RecommendRequest request = RecommendRequest.builder()
            .userId(testUser.getId().toString())
            .size(10)
            .contentType("article")
            .build();

        // When
        List<RecallResult> results = multiPathRecallService.executeMultiPathRecall(request);
        List<Content> mergedContents = multiPathRecallService.mergeRecallResults(results, 10);

        // Then
        assertNotNull(mergedContents);

        // 验证所有内容都是文章类型
        for (Content content : mergedContents) {
            assertEquals(Content.ContentType.ARTICLE, content.getContentType());
        }
    }

    private Content createTestContent(String title, Content.ContentType type, Integer categoryId, List<String> tags) {
        Content content = new Content();
        content.setTitle(title);
        content.setContentType(type);
        content.setCategoryId(categoryId);
        content.setTags(tags);
        content.setStatus(Content.ContentStatus.PUBLISHED);
        content.setHotScore(BigDecimal.valueOf(Math.random() * 100));
        content.setViewCount(100L);
        content.setLikeCount(10L);
        content.setShareCount(5L);
        content.setCommentCount(3L);
        content.setPublishTime(LocalDateTime.now().minusDays(1));
        content.setContentData(java.util.Map.of("summary", "Test content summary"));
        return content;
    }

    private void createUserBehavior(Long userId, Long contentId, UserBehavior.ActionType actionType) {
        UserBehavior behavior = new UserBehavior();
        behavior.setUserId(userId);
        behavior.setContentId(contentId);
        behavior.setActionType(actionType);
        behavior.setSessionId("test-session");
        behavior.setDeviceType("web");
        behavior.setDuration(60);
        userBehaviorRepository.save(behavior);
    }
}