package com.recommendation.user.dto;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户画像响应DTO
 */
@Data
@Builder
public class UserProfileResponse {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 兴趣标签权重
     */
    private Map<String, Double> interestWeights;

    /**
     * 内容类型偏好度
     */
    private Map<String, Double> contentTypePreferences;

    /**
     * 行为特征
     */
    private Map<String, Object> behaviorFeatures;

    /**
     * 人口统计学特征
     */
    private Map<String, Object> demographicFeatures;

    /**
     * 活跃度分数
     */
    private Double activityScore;

    /**
     * 画像更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 画像质量分数
     */
    private Double profileQualityScore;
}