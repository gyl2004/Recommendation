package com.recommendation.service.service;

import com.recommendation.service.dto.RecommendResponse;
import java.util.List;

/**
 * 重排序服务接口
 */
public interface ReRankingService {
    
    /**
     * 重排序
     *
     * @param userId 用户ID
     * @param items 待重排序项目
     * @param diversityThreshold 多样性阈值
     * @return 重排序后的结果
     */
    List<RecommendResponse.RecommendItem> reRank(String userId, 
                                                List<RecommendResponse.RecommendItem> items, 
                                                Double diversityThreshold);
    
    /**
     * 多样性处理
     *
     * @param items 推荐项列表
     * @param diversityThreshold 多样性阈值
     * @return 多样性处理后的结果
     */
    List<RecommendResponse.RecommendItem> diversityProcess(List<RecommendResponse.RecommendItem> items, 
                                                          Double diversityThreshold);
    
    /**
     * 业务规则过滤
     *
     * @param userId 用户ID
     * @param items 推荐项列表
     * @return 过滤后的结果
     */
    List<RecommendResponse.RecommendItem> businessRuleFilter(String userId, 
                                                            List<RecommendResponse.RecommendItem> items);
    
    /**
     * 内容质量评估
     *
     * @param items 推荐项列表
     * @return 质量评估后的结果
     */
    List<RecommendResponse.RecommendItem> qualityAssessment(List<RecommendResponse.RecommendItem> items);
}