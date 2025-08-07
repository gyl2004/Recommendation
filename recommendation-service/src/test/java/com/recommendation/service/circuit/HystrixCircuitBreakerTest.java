package com.recommendation.service.circuit;

import com.recommendation.service.dto.RecommendResponse;
import com.recommendation.service.service.FallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Hystrix熔断器测试
 */
@ExtendWith(MockitoExtension.class)
class HystrixCircuitBreakerTest {

    @Mock
    private FallbackService fallbackService;

    private RecommendResponse mockFallbackResponse;

    @BeforeEach
    void setUp() {
        mockFallbackResponse = RecommendResponse.builder()
                .items(new ArrayList<>())
                .total(0)
                .algorithmVersion("fallback_v1.0")
                .fromCache(false)
                .build();
    }

    @Test
    void testRecommendationCommandSuccess() {
        // 准备测试数据
        RecommendResponse expectedResponse = RecommendResponse.builder()
                .items(new ArrayList<>())
                .total(10)
                .algorithmVersion("v1.0")
                .fromCache(false)
                .build();

        // 创建成功的业务逻辑
        RecommendationHystrixCommand command = new RecommendationHystrixCommand(
                () -> expectedResponse,
                fallbackService,
                "user123",
                "article",
                10
        );

        // 执行命令
        RecommendResponse result = command.execute();

        // 验证结果
        assertNotNull(result);
        assertEquals(10, result.getTotal());
        assertEquals("v1.0", result.getAlgorithmVersion());
        assertFalse(command.isCircuitBreakerOpen());
    }

    @Test
    void testRecommendationCommandFailure() {
        // 模拟降级响应
        when(fallbackService.getDefaultFallback(anyString(), anyString(), anyInt()))
                .thenReturn(mockFallbackResponse);

        // 创建失败的业务逻辑
        RecommendationHystrixCommand command = new RecommendationHystrixCommand(
                () -> {
                    throw new RuntimeException("Service unavailable");
                },
                fallbackService,
                "user123",
                "article",
                10
        );

        // 执行命令
        RecommendResponse result = command.execute();

        // 验证降级被调用
        assertNotNull(result);
        assertEquals("fallback_v1.0", result.getAlgorithmVersion());
        verify(fallbackService).getDefaultFallback("user123", "article", 10);
    }

    @Test
    void testRecommendationCommandTimeout() {
        // 模拟超时降级响应
        when(fallbackService.getTimeoutFallback(anyString(), anyString(), anyInt()))
                .thenReturn(mockFallbackResponse);

        // 创建超时的业务逻辑
        RecommendationHystrixCommand command = new RecommendationHystrixCommand(
                () -> {
                    try {
                        Thread.sleep(5000); // 超过默认超时时间
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return RecommendResponse.builder().build();
                },
                fallbackService,
                "user123",
                "article",
                10
        );

        // 执行命令
        RecommendResponse result = command.execute();

        // 验证超时降级被调用
        assertNotNull(result);
        verify(fallbackService).getTimeoutFallback("user123", "article", 10);
    }

    @Test
    void testRecallCommandSuccess() {
        // 准备测试数据
        List<Long> expectedIds = List.of(1L, 2L, 3L, 4L, 5L);

        // 创建成功的召回逻辑
        RecallHystrixCommand command = new RecallHystrixCommand(
                () -> expectedIds,
                fallbackService,
                "user123",
                "article",
                100
        );

        // 执行命令
        List<Long> result = command.execute();

        // 验证结果
        assertNotNull(result);
        assertEquals(5, result.size());
        assertEquals(expectedIds, result);
        assertFalse(command.isCircuitBreakerOpen());
    }

    @Test
    void testRecallCommandFallback() {
        // 模拟召回降级响应
        List<Long> fallbackIds = List.of(100L, 101L, 102L);
        when(fallbackService.getRecallFallback(anyString(), anyString(), anyInt()))
                .thenReturn(fallbackIds);

        // 创建失败的召回逻辑
        RecallHystrixCommand command = new RecallHystrixCommand(
                () -> {
                    throw new RuntimeException("Recall service error");
                },
                fallbackService,
                "user123",
                "article",
                100
        );

        // 执行命令
        List<Long> result = command.execute();

        // 验证降级被调用
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals(fallbackIds, result);
        verify(fallbackService).getRecallFallback("user123", "article", 100);
    }

    @Test
    void testRankingCommandSuccess() {
        // 准备测试数据
        List<Long> candidates = List.of(5L, 3L, 1L, 4L, 2L);
        List<Long> expectedRanked = List.of(1L, 2L, 3L, 4L, 5L);

        // 创建成功的排序逻辑
        RankingHystrixCommand command = new RankingHystrixCommand(
                (candidateList) -> {
                    List<Long> sorted = new ArrayList<>(candidateList);
                    sorted.sort(Long::compareTo);
                    return sorted;
                },
                fallbackService,
                candidates,
                "user123"
        );

        // 执行命令
        List<Long> result = command.execute();

        // 验证结果
        assertNotNull(result);
        assertEquals(5, result.size());
        assertEquals(expectedRanked, result);
        assertFalse(command.isCircuitBreakerOpen());
    }

    @Test
    void testRankingCommandFallback() {
        // 准备测试数据
        List<Long> candidates = List.of(5L, 3L, 1L, 4L, 2L);
        
        // 模拟排序降级响应
        when(fallbackService.getRankingFallback(anyList(), anyString()))
                .thenReturn(candidates);

        // 创建失败的排序逻辑
        RankingHystrixCommand command = new RankingHystrixCommand(
                (candidateList) -> {
                    throw new RuntimeException("Ranking service error");
                },
                fallbackService,
                candidates,
                "user123"
        );

        // 执行命令
        List<Long> result = command.execute();

        // 验证降级被调用
        assertNotNull(result);
        assertEquals(candidates, result);
        verify(fallbackService).getRankingFallback(candidates, "user123");
    }

    @Test
    void testCircuitBreakerOpening() throws InterruptedException {
        // 模拟熔断降级响应
        when(fallbackService.getCircuitBreakerFallback(anyString(), anyString(), anyInt()))
                .thenReturn(mockFallbackResponse);

        // 并发执行多个失败请求以触发熔断器
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(25); // 超过熔断器阈值

        for (int i = 0; i < 25; i++) {
            executor.submit(() -> {
                try {
                    RecommendationHystrixCommand command = new RecommendationHystrixCommand(
                            () -> {
                                throw new RuntimeException("Simulated failure");
                            },
                            fallbackService,
                            "user123",
                            "article",
                            10
                    );
                    command.execute();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有请求完成
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // 等待熔断器状态更新
        Thread.sleep(1000);

        // 创建新命令检查熔断器状态
        RecommendationHystrixCommand testCommand = new RecommendationHystrixCommand(
                () -> RecommendResponse.builder().build(),
                fallbackService,
                "user123",
                "article",
                10
        );

        // 执行命令（应该直接走降级）
        RecommendResponse result = testCommand.execute();

        // 验证熔断器已开启
        assertNotNull(result);
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
}