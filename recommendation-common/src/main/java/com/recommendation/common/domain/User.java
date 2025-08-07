package com.recommendation.common.domain;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户领域模型
 * 支持需求1.1: 用户行为数据收集
 * 支持需求4.1-4.4: 用户画像构建
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    /**
     * 用户ID
     */
    private Long id;
    
    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50个字符之间")
    private String username;
    
    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    private String email;
    
    /**
     * 手机号
     */
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    private String phone;
    
    /**
     * 用户画像数据 (JSON格式存储)
     * 包含年龄组、兴趣标签、行为偏好等信息
     */
    private Map<String, Object> profileData;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 获取用户年龄组
     */
    public String getAgeGroup() {
        if (profileData != null && profileData.containsKey("age_group")) {
            return (String) profileData.get("age_group");
        }
        return "unknown";
    }
    
    /**
     * 获取用户兴趣标签
     */
    @SuppressWarnings("unchecked")
    public java.util.List<String> getInterestTags() {
        if (profileData != null && profileData.containsKey("interests")) {
            Object interests = profileData.get("interests");
            if (interests instanceof java.util.List) {
                return (java.util.List<String>) interests;
            }
        }
        return java.util.Collections.emptyList();
    }
    
    /**
     * 获取用户行为评分
     */
    public Double getBehaviorScore() {
        if (profileData != null && profileData.containsKey("behavior_score")) {
            Object score = profileData.get("behavior_score");
            if (score instanceof Number) {
                return ((Number) score).doubleValue();
            }
        }
        return 0.0;
    }
}