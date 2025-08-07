package com.recommendation.user.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 用户画像更新请求DTO
 */
@Data
public class UserProfileRequest {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 兴趣标签权重
     * key: 标签名称, value: 权重值(0.0-1.0)
     */
    private Map<String, Double> interestWeights;

    /**
     * 内容类型偏好度
     * key: 内容类型, value: 偏好度(0.0-1.0)
     */
    private Map<String, Double> contentTypePreferences;

    /**
     * 行为特征
     * key: 特征名称, value: 特征值
     */
    private Map<String, Object> behaviorFeatures;

    /**
     * 人口统计学特征
     * 如年龄、性别、地区等
     */
    private Map<String, Object> demographicFeatures;
}