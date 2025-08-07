package com.recommendation.service.recall;

import com.recommendation.common.dto.RecommendRequest;
import com.recommendation.common.entity.Content;

import java.util.List;

/**
 * 召回服务接口
 * 支持需求5.1, 5.2: 推荐算法引擎 - 多路召回算法
 */
public interface RecallService {
    
    /**
     * 多路召回
     * 
     * @param request 推荐请求
     * @return 召回的内容候选集
     */
    List<Content> multiPathRecall(RecommendRequest request);
    
    /**
     * 获取召回算法名称
     * 
     * @return 算法名称
     */
    String getAlgorithmName();
    
    /**
     * 获取召回权重
     * 
     * @return 权重值
     */
    double getWeight();
}