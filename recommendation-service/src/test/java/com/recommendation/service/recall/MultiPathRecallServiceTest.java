package com.recommendation.service.recall;

import com.recommendation.common.dto.RecommendRequest;
import com.recommendation.common.entity.Content;
import com.recommendation.service.recall.dto.RecallResult;
import com.recommendation.service.recall.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 多路召回服务测试
 * 支持需求5.1, 5.2: 推荐算法引擎 - 多路召回算法测试
 */
@ExtendWith(MockitoExtension.class)
class MultiPathRecallServiceTest {

    @Mock
    private CollaborativeFilteringRecallService collaborativeFilteringRecall;

    @Mock
    private ContentSimilarityRecallService contentSimilarityRecall;

    @Mock
    private HotContentRecallService hotContentRecall;

    @Mock
    private UserHistoryRecallService userHistoryRecall;

    @InjectMocks
    private MultiPathRecallService multiPathRecallService;

    private RecommendRequest request;
    private Content testContent1;
    private Content testContent2;

    @BeforeEach
    void setUp() {
        request = RecommendRequest.builder()
            .userId("1")
            .size(10)
            .contentType("mixed")
            .build();

        testContent1 = new Content();
        testContent1.setId(1L);
        testContent1.setTitle("Test Content 1");
        testContent1.setContentType(Content.ContentType.ARTICLE);
        testContent1.setStatus(Content.ContentStatus.PUBLISHED);
        testContent1.setHotScore(BigDecimal.valueOf(10.0));

        testContent2 = new Content();
        testContent2.setId(2L);
        testContent2.setTitle("Test Content 2");
        testContent2.setContentType(Content.ContentType.VIDEO);
        testContent2.setStatus(Content.ContentStatus.PUBLISHED);
        testContent2.setHotScore(BigDecimal.valueOf(8.0));
    }

    @Test
    void testExecuteMultiPathRecall_WithAllAlgorithms_ShouldReturnResults() {
        // Given
        List<Content> cfContents = Arrays.asList(testContent1);
        List<Content> csContents = Arrays.asList(testContent2);
        List<Content> hotContents = Arrays.asList(testContent1, testContent2);
        List<Content> historyContents = Arrays.asList(testContent1);

        when(collaborativeFilteringRecall.multiPathRecall(any(RecommendRequest.class)))
            .thenReturn(cfContents);
        when(collaborativeFilteringRecall.getAlgorithmName())
            .thenReturn("collaborative_filtering");
        when(collaborativeFilteringRecall.getWeight())
            .thenReturn(0.3);

        when(contentSimilarityRecall.multiPathRecall(any(RecommendRequest.class)))
            .thenReturn(csContents);
        when(contentSimilarityRecall.getAlgorithmName())
            .thenReturn("content_similarity");
        when(contentSimilarityRecall.getWeight())
            .thenReturn(0.25);

        when(hotContentRecall.multiPathRecall(any(RecommendRequest.class)))
            .thenReturn(hotContents);
        when(hotContentRecall.getAlgorithmName())
            .thenReturn("hot_content");
        when(hotContentRecall.getWeight())
            .thenReturn(0.2);

        when(userHistoryRecall.multiPathRecall(any(RecommendRequest.class)))
            .thenReturn(historyContents);
        when(userHistoryRecall.getAlgorithmName())
            .thenReturn("user_history");
        when(userHistoryRecall.getWeight())
            .thenReturn(0.25);

        // When
        List<RecallResult> results = multiPathRecallService.executeMultiPathRecall(request);

        // Then
        assertNotNull(results);
        assertEquals(4, results.size());

        // 验证每个算法都被调用
        verify(collaborativeFilteringRecall).multiPathRecall(any(RecommendRequest.class));
        verify(contentSimilarityRecall).multiPathRecall(any(RecommendRequest.class));
        verify(hotContentRecall).multiPathRecall(any(RecommendRequest.class));
        verify(userHistoryRecall).multiPathRecall(any(RecommendRequest.class));
    }

    @Test
    void testMergeRecallResults_WithMultipleResults_ShouldMergeCorrectly() {
        // Given
        RecallResult result1 = RecallResult.builder()
            .contents(Arrays.asList(testContent1))
            .algorithm("collaborative_filtering")
            .weight(0.3)
            .count(1)
            .duration(100L)
            .build();

        RecallResult result2 = RecallResult.builder()
            .contents(Arrays.asList(testContent2))
            .algorithm("content_similarity")
            .weight(0.25)
            .count(1)
            .duration(80L)
            .build();

        RecallResult result3 = RecallResult.builder()
            .contents(Arrays.asList(testContent1, testContent2)) // 重复内容
            .algorithm("hot_content")
            .weight(0.2)
            .count(2)
            .duration(60L)
            .build();

        List<RecallResult> recallResults = Arrays.asList(result1, result2, result3);

        // When
        List<Content> mergedContents = multiPathRecallService.mergeRecallResults(recallResults, 10);

        // Then
        assertNotNull(mergedContents);
        assertEquals(2, mergedContents.size()); // 去重后应该有2个内容

        // testContent1应该排在前面，因为它出现在更多算法中
        assertEquals(testContent1.getId(), mergedContents.get(0).getId());
        assertEquals(testContent2.getId(), mergedContents.get(1).getId());
    }

    @Test
    void testMergeRecallResults_WithEmptyResults_ShouldReturnEmptyList() {
        // Given
        List<RecallResult> emptyResults = Collections.emptyList();

        // When
        List<Content> mergedContents = multiPathRecallService.mergeRecallResults(emptyResults, 10);

        // Then
        assertNotNull(mergedContents);
        assertTrue(mergedContents.isEmpty());
    }

    @Test
    void testMergeRecallResults_WithTargetSizeLimit_ShouldLimitResults() {
        // Given
        RecallResult result = RecallResult.builder()
            .contents(Arrays.asList(testContent1, testContent2))
            .algorithm("hot_content")
            .weight(0.2)
            .count(2)
            .duration(60L)
            .build();

        List<RecallResult> recallResults = Arrays.asList(result);

        // When
        List<Content> mergedContents = multiPathRecallService.mergeRecallResults(recallResults, 1);

        // Then
        assertNotNull(mergedContents);
        assertEquals(1, mergedContents.size()); // 应该被限制为1个
    }

    @Test
    void testExecuteMultiPathRecall_WithException_ShouldHandleGracefully() {
        // Given
        when(collaborativeFilteringRecall.multiPathRecall(any(RecommendRequest.class)))
            .thenThrow(new RuntimeException("Algorithm error"));
        when(collaborativeFilteringRecall.getAlgorithmName())
            .thenReturn("collaborative_filtering");

        when(contentSimilarityRecall.multiPathRecall(any(RecommendRequest.class)))
            .thenReturn(Arrays.asList(testContent1));
        when(contentSimilarityRecall.getAlgorithmName())
            .thenReturn("content_similarity");
        when(contentSimilarityRecall.getWeight())
            .thenReturn(0.25);

        when(hotContentRecall.multiPathRecall(any(RecommendRequest.class)))
            .thenReturn(Arrays.asList(testContent2));
        when(hotContentRecall.getAlgorithmName())
            .thenReturn("hot_content");
        when(hotContentRecall.getWeight())
            .thenReturn(0.2);

        when(userHistoryRecall.multiPathRecall(any(RecommendRequest.class)))
            .thenReturn(Collections.emptyList());
        when(userHistoryRecall.getAlgorithmName())
            .thenReturn("user_history");
        when(userHistoryRecall.getWeight())
            .thenReturn(0.25);

        // When
        List<RecallResult> results = multiPathRecallService.executeMultiPathRecall(request);

        // Then
        assertNotNull(results);
        assertEquals(3, results.size()); // 应该有3个成功的结果，1个失败的被过滤掉
    }
}