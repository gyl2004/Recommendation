package com.recommendation.service.service;

import com.recommendation.service.dto.RecommendRequest;
import com.recommendation.service.dto.RecommendResponse;

/**
 * 推荐服务接口
 */
public interface RecommendationService {
    
    /**
     * 获取个性化推荐
     *
     * @param request 推荐请求
     * @param requestId 请求ID
     * @return 推荐结果
     */
    RecommendResponse recommend(RecommendRequest request, String requestId);
    
    /**
     * 获取推荐解释
     *
     * @param userId 用户ID
     * @param contentId 内容ID
     * @param requestId 请求ID
     * @return 推荐解释
     */
    String explainRecommendation(String userId, String contentId, String requestId);
    
    /**
     * 预热缓存
     *
     * @param userId 用户ID
     */
    void warmupCache(String userId);
}