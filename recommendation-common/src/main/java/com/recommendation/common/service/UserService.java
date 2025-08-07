package com.recommendation.common.service;

import com.recommendation.common.domain.User;
import com.recommendation.common.dto.UserBehaviorDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户服务接口
 * 支持需求4.1-4.4: 用户画像构建
 */
public interface UserService {
    
    /**
     * 根据ID获取用户信息
     * 
     * @param userId 用户ID
     * @return 用户信息
     */
    Optional<User> getUserById(Long userId);
    
    /**
     * 创建用户
     * 
     * @param user 用户信息
     * @return 创建的用户
     */
    User createUser(User user);
    
    /**
     * 更新用户信息
     * 
     * @param user 用户信息
     * @return 更新后的用户
     */
    User updateUser(User user);
    
    /**
     * 记录用户行为
     * 
     * @param behavior 用户行为数据
     */
    void recordUserBehavior(UserBehaviorDto behavior);
    
    /**
     * 获取用户画像特征
     * 
     * @param userId 用户ID
     * @return 用户特征数据
     */
    Map<String, Object> getUserFeatures(Long userId);
    
    /**
     * 更新用户画像
     * 
     * @param userId 用户ID
     * @param features 特征数据
     */
    void updateUserProfile(Long userId, Map<String, Object> features);
    
    /**
     * 获取用户兴趣标签
     * 
     * @param userId 用户ID
     * @return 兴趣标签列表
     */
    List<String> getUserInterestTags(Long userId);
}