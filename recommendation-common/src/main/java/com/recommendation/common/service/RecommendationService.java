package com.recommendation.common.service;

import com.recommendation.common.dto.RecommendRequest;
import com.recommendation.common.dto.RecommendResponse;
import com.recommendation.common.dto.FeedbackRequest;

/**
 * 推荐服务接口
 * 支持需求3.1-3.4: 实时推荐服务
 * 支持需求5.1-5.4: 推荐算法引擎
 */
public interface RecommendationService {
    
    /**
     * 获取个性化推荐内容
     * 
     * @param request 推荐请求
     * @return 推荐响应
     */
    RecommendResponse getRecommendations(RecommendRequest request);
    
    /**
     * 记录推荐反馈
     * 
     * @param feedback 反馈请求
     */
    void recordFeedback(FeedbackRequest feedback);
    
    /**
     * 获取热门内容推荐(降级方案)
     * 
     * @param contentType 内容类型
     * @param size 推荐数量
     * @return 推荐响应
     */
    RecommendResponse getHotRecommendations(String contentType, Integer size);
}