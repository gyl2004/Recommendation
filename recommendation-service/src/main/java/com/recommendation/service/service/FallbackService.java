package com.recommendation.service.service;

import com.recommendation.service.dto.RecommendRequest;
import com.recommendation.service.dto.RecommendResponse;

import java.util.List;

/**
 * 降级服务接口
 */
public interface FallbackService {
    
    /**
     * 获取降级推荐结果
     *
     * @param request 推荐请求
     * @param requestId 请求ID
     * @return 降级推荐结果
     */
    RecommendResponse getFallbackRecommendation(RecommendRequest request, String requestId);
    
    /**
     * 获取热门内容作为降级方案
     *
     * @param contentType 内容类型
     * @param size 数量
     * @return 热门内容列表
     */
    RecommendResponse getHotContentFallback(String contentType, Integer size);
    
    /**
     * 获取默认推荐内容
     *
     * @param contentType 内容类型
     * @param size 数量
     * @return 默认推荐内容
     */
    RecommendResponse getDefaultRecommendation(String contentType, Integer size);

    // ========== 多级降级策略方法 ==========
    
    /**
     * 熔断器开启时的降级方案
     *
     * @param userId 用户ID
     * @param contentType 内容类型
     * @param size 数量
     * @return 降级推荐结果
     */
    RecommendResponse getCircuitBreakerFallback(String userId, String contentType, Integer size);
    
    /**
     * 超时降级方案
     *
     * @param userId 用户ID
     * @param contentType 内容类型
     * @param size 数量
     * @return 降级推荐结果
     */
    RecommendResponse getTimeoutFallback(String userId, String contentType, Integer size);
    
    /**
     * 默认降级方案
     *
     * @param userId 用户ID
     * @param contentType 内容类型
     * @param size 数量
     * @return 降级推荐结果
     */
    RecommendResponse getDefaultFallback(String userId, String contentType, Integer size);
    
    /**
     * 召回服务降级方案
     *
     * @param userId 用户ID
     * @param contentType 内容类型
     * @param size 数量
     * @return 候选内容ID列表
     */
    List<Long> getRecallFallback(String userId, String contentType, Integer size);
    
    /**
     * 排序服务降级方案
     *
     * @param candidates 候选内容列表
     * @param userId 用户ID
     * @return 排序后的内容ID列表
     */
    List<Long> getRankingFallback(List<Long> candidates, String userId);
}