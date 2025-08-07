package com.recommendation.service.recall.impl;

import com.recommendation.common.dto.RecommendRequest;
import com.recommendation.common.entity.Content;
import com.recommendation.common.entity.UserBehavior;
import com.recommendation.common.repository.ContentRepository;
import com.recommendation.common.repository.UserBehaviorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 协同过滤召回服务测试
 * 支持需求5.1, 5.2: 推荐算法引擎 - 协同过滤召回算法测试
 */
@ExtendWith(MockitoExtension.class)
class CollaborativeFilteringRecallServiceTest {

    @Mock
    private UserBehaviorRepository userBehaviorRepository;

    @Mock
    private ContentRepository contentRepository;

    @InjectMocks
    private CollaborativeFilteringRecallService recallService;

    private RecommendRequest request;
    private Content testContent;
    private UserBehavior testBehavior;

    @BeforeEach
    void setUp() {
        request = RecommendRequest.builder()
            .userId("1")
            .size(10)
            .contentType("mixed")
            .build();

        testContent = new Content();
        testContent.setId(1L);
        testContent.setTitle("Test Content");
        testContent.setContentType(Content.ContentType.ARTICLE);
        testContent.setStatus(Content.ContentStatus.PUBLISHED);
        testContent.setHotScore(BigDecimal.valueOf(10.0));

        testBehavior = new UserBehavior();
        testBehavior.setId(1L);
        testBehavior.setUserId(2L);
        testBehavior.setContentId(1L);
        testBehavior.setActionType(UserBehavior.ActionType.LIKE);
    }

    @Test
    void testMultiPathRecall_WithSimilarUsers_ShouldReturnContents() {
        // Given
        List<Object[]> similarUsers = Arrays.asList(
            new Object[]{2L, 5L},
            new Object[]{3L, 4L}
        );
        List<Long> viewedContentIds = Collections.emptyList();
        List<UserBehavior> preferredBehaviors = Arrays.asList(testBehavior);
        List<Content> contents = Arrays.asList(testContent);

        when(userBehaviorRepository.findSimilarUsers(eq(1L), anyLong()))
            .thenReturn(similarUsers);
        when(userBehaviorRepository.findRecentViewedContentIds(eq(1L), any(LocalDateTime.class)))
            .thenReturn(viewedContentIds);
        when(userBehaviorRepository.findUserPreferredContents(anyLong()))
            .thenReturn(preferredBehaviors);
        when(contentRepository.findAllById(anyList()))
            .thenReturn(contents);

        // When
        List<Content> result = recallService.multiPathRecall(request);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testContent.getId(), result.get(0).getId());
        
        verify(userBehaviorRepository).findSimilarUsers(eq(1L), anyLong());
        verify(userBehaviorRepository).findRecentViewedContentIds(eq(1L), any(LocalDateTime.class));
        verify(userBehaviorRepository, atLeastOnce()).findUserPreferredContents(anyLong());
        verify(contentRepository).findAllById(anyList());
    }

    @Test
    void testMultiPathRecall_WithNoSimilarUsers_ShouldReturnEmptyList() {
        // Given
        when(userBehaviorRepository.findSimilarUsers(eq(1L), anyLong()))
            .thenReturn(Collections.emptyList());

        // When
        List<Content> result = recallService.multiPathRecall(request);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        verify(userBehaviorRepository).findSimilarUsers(eq(1L), anyLong());
        verify(userBehaviorRepository, never()).findUserPreferredContents(anyLong());
    }

    @Test
    void testMultiPathRecall_WithViewedContent_ShouldFilterOut() {
        // Given
        List<Object[]> similarUsers = Arrays.asList(new Object[]{2L, 5L});
        List<Long> viewedContentIds = Arrays.asList(1L); // 已浏览的内容
        List<UserBehavior> preferredBehaviors = Arrays.asList(testBehavior);

        when(userBehaviorRepository.findSimilarUsers(eq(1L), anyLong()))
            .thenReturn(similarUsers);
        when(userBehaviorRepository.findRecentViewedContentIds(eq(1L), any(LocalDateTime.class)))
            .thenReturn(viewedContentIds);
        when(userBehaviorRepository.findUserPreferredContents(anyLong()))
            .thenReturn(preferredBehaviors);

        // When
        List<Content> result = recallService.multiPathRecall(request);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty()); // 应该被过滤掉
        
        verify(contentRepository, never()).findAllById(anyList());
    }

    @Test
    void testMultiPathRecall_WithException_ShouldReturnEmptyList() {
        // Given
        when(userBehaviorRepository.findSimilarUsers(eq(1L), anyLong()))
            .thenThrow(new RuntimeException("Database error"));

        // When
        List<Content> result = recallService.multiPathRecall(request);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetAlgorithmName() {
        // When
        String algorithmName = recallService.getAlgorithmName();

        // Then
        assertEquals("collaborative_filtering", algorithmName);
    }

    @Test
    void testGetWeight() {
        // When
        double weight = recallService.getWeight();

        // Then
        assertEquals(0.3, weight, 0.001);
    }

    @Test
    void testMultiPathRecall_WithContentTypeFilter_ShouldFilterByType() {
        // Given
        request.setContentType("article");
        
        List<Object[]> similarUsers = Arrays.asList(new Object[]{2L, 5L});
        List<Long> viewedContentIds = Collections.emptyList();
        List<UserBehavior> preferredBehaviors = Arrays.asList(testBehavior);
        
        Content videoContent = new Content();
        videoContent.setId(2L);
        videoContent.setContentType(Content.ContentType.VIDEO);
        videoContent.setStatus(Content.ContentStatus.PUBLISHED);
        
        List<Content> contents = Arrays.asList(testContent, videoContent);

        when(userBehaviorRepository.findSimilarUsers(eq(1L), anyLong()))
            .thenReturn(similarUsers);
        when(userBehaviorRepository.findRecentViewedContentIds(eq(1L), any(LocalDateTime.class)))
            .thenReturn(viewedContentIds);
        when(userBehaviorRepository.findUserPreferredContents(anyLong()))
            .thenReturn(preferredBehaviors);
        when(contentRepository.findAllById(anyList()))
            .thenReturn(contents);

        // When
        List<Content> result = recallService.multiPathRecall(request);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Content.ContentType.ARTICLE, result.get(0).getContentType());
    }
}