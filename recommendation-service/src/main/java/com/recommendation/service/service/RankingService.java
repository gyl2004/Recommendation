package com.recommendation.service.service;

import com.recommendation.service.dto.RecommendResponse;
import java.util.List;

/**
 * 排序服务接口
 */
public interface RankingService {
    
    /**
     * 精确排序
     *
     * @param userId 用户ID
     * @param items 待排序项目
     * @param scene 场景
     * @return 排序后的结果
     */
    List<RecommendResponse.RecommendItem> rank(String userId, 
                                               List<RecommendResponse.RecommendItem> items, 
                                               String scene);
    
    /**
     * 计算推荐分数
     *
     * @param userId 用户ID
     * @param item 推荐项
     * @param scene 场景
     * @return 推荐分数
     */
    Double calculateScore(String userId, RecommendResponse.RecommendItem item, String scene);
    
    /**
     * 批量计算推荐分数
     *
     * @param userId 用户ID
     * @param items 推荐项列表
     * @param scene 场景
     * @return 分数列表
     */
    List<Double> batchCalculateScore(String userId, 
                                   List<RecommendResponse.RecommendItem> items, 
                                   String scene);
}