package com.recommendation.service.service;

import com.recommendation.service.dto.RecommendResponse;
import java.util.List;

/**
 * 召回服务接口
 */
public interface RecallService {
    
    /**
     * 多路召回
     *
     * @param userId 用户ID
     * @param contentType 内容类型
     * @param size 召回数量
     * @return 召回结果
     */
    List<RecommendResponse.RecommendItem> recall(String userId, String contentType, Integer size);
    
    /**
     * 协同过滤召回
     *
     * @param userId 用户ID
     * @param size 召回数量
     * @return 召回结果
     */
    List<RecommendResponse.RecommendItem> collaborativeFilteringRecall(String userId, Integer size);
    
    /**
     * 内容相似度召回
     *
     * @param userId 用户ID
     * @param contentType 内容类型
     * @param size 召回数量
     * @return 召回结果
     */
    List<RecommendResponse.RecommendItem> contentBasedRecall(String userId, String contentType, Integer size);
    
    /**
     * 热门内容召回
     *
     * @param contentType 内容类型
     * @param size 召回数量
     * @return 召回结果
     */
    List<RecommendResponse.RecommendItem> hotContentRecall(String contentType, Integer size);
}