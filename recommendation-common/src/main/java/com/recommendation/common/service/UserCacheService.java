package com.recommendation.common.service;

import com.recommendation.common.constant.CacheConstants;
import com.recommendation.common.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户缓存服务类
 * 管理用户相关的缓存数据
 */
@Slf4j
@Service
public class UserCacheService {

    @Autowired
    private CacheService cacheService;

    /**
     * 缓存用户基础信息
     */
    public void cacheUserInfo(User user) {
        String key = CacheConstants.buildKey(CacheConstants.User.INFO, String.valueOf(user.getId()));
        cacheService.set(key, user, CacheConstants.LONG_EXPIRE_TIME);
        log.info("Cached user info: userId={}", user.getId());
    }

    /**
     * 获取用户基础信息
     */
    public User getUserInfo(Long userId) {
        String key = CacheConstants.buildKey(CacheConstants.User.INFO, String.valueOf(userId));
        return cacheService.get(key, User.class);
    }

    /**
     * 删除用户基础信息缓存
     */
    public void deleteUserInfo(Long userId) {
        String key = CacheConstants.buildKey(CacheConstants.User.INFO, String.valueOf(userId));
        cacheService.delete(key);
        log.info("Deleted user info cache: userId={}", userId);
    }

    /**
     * 缓存用户特征数据
     */
    public void cacheUserFeatures(Long userId, Map<String, Object> features) {
        String key = CacheConstants.buildUserFeaturesKey(userId);
        cacheService.hMSet(key, features);
        cacheService.expire(key, CacheConstants.DEFAULT_EXPIRE_TIME);
        log.info("Cached user features: userId={}, featuresCount={}", userId, features.size());
    }

    /**
     * 获取用户特征数据
     */
    public Map<Object, Object> getUserFeatures(Long userId) {
        String key = CacheConstants.buildUserFeaturesKey(userId);
        return cacheService.hGetAll(key);
    }

    /**
     * 获取用户特定特征
     */
    public Object getUserFeature(Long userId, String featureName) {
        String key = CacheConstants.buildUserFeaturesKey(userId);
        return cacheService.hGet(key, featureName);
    }

    /**
     * 更新用户特征
     */
    public void updateUserFeature(Long userId, String featureName, Object featureValue) {
        String key = CacheConstants.buildUserFeaturesKey(userId);
        cacheService.hSet(key, featureName, featureValue);
        log.debug("Updated user feature: userId={}, feature={}", userId, featureName);
    }

    /**
     * 删除用户特征缓存
     */
    public void deleteUserFeatures(Long userId) {
        String key = CacheConstants.buildUserFeaturesKey(userId);
        cacheService.delete(key);
        log.info("Deleted user features cache: userId={}", userId);
    }

    /**
     * 缓存用户偏好标签
     */
    public void cacheUserPreferences(Long userId, Map<String, Double> preferences) {
        String key = CacheConstants.buildKey(CacheConstants.User.PREFERENCES, String.valueOf(userId));
        Map<String, Object> prefMap = new HashMap<>();
        preferences.forEach((tag, score) -> prefMap.put(tag, score));
        cacheService.hMSet(key, prefMap);
        cacheService.expire(key, CacheConstants.DEFAULT_EXPIRE_TIME);
        log.info("Cached user preferences: userId={}, preferencesCount={}", userId, preferences.size());
    }

    /**
     * 获取用户偏好标签
     */
    public Map<Object, Object> getUserPreferences(Long userId) {
        String key = CacheConstants.buildKey(CacheConstants.User.PREFERENCES, String.valueOf(userId));
        return cacheService.hGetAll(key);
    }

    /**
     * 获取用户对特定标签的偏好分数
     */
    public Double getUserPreferenceScore(Long userId, String tag) {
        String key = CacheConstants.buildKey(CacheConstants.User.PREFERENCES, String.valueOf(userId));
        Object score = cacheService.hGet(key, tag);
        return score instanceof Number ? ((Number) score).doubleValue() : 0.0;
    }

    /**
     * 更新用户偏好分数
     */
    public void updateUserPreference(Long userId, String tag, Double score) {
        String key = CacheConstants.buildKey(CacheConstants.User.PREFERENCES, String.valueOf(userId));
        cacheService.hSet(key, tag, score);
        log.debug("Updated user preference: userId={}, tag={}, score={}", userId, tag, score);
    }

    /**
     * 缓存用户行为历史
     */
    public void cacheUserBehaviorHistory(Long userId, List<Map<String, Object>> behaviors) {
        String key = CacheConstants.buildKey(CacheConstants.User.BEHAVIOR, String.valueOf(userId));
        // 清空旧数据
        cacheService.delete(key);
        // 添加新数据
        if (!behaviors.isEmpty()) {
            cacheService.rPush(key, behaviors.toArray());
            cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
        }
        log.info("Cached user behavior history: userId={}, behaviorsCount={}", userId, behaviors.size());
    }

    /**
     * 添加用户行为记录
     */
    public void addUserBehavior(Long userId, Map<String, Object> behavior) {
        String key = CacheConstants.buildKey(CacheConstants.User.BEHAVIOR, String.valueOf(userId));
        cacheService.lPush(key, behavior);
        // 保持最近100条记录
        cacheService.lRange(key, 0, 99);
        cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
        log.debug("Added user behavior: userId={}", userId);
    }

    /**
     * 获取用户行为历史
     */
    public List<Object> getUserBehaviorHistory(Long userId, int limit) {
        String key = CacheConstants.buildKey(CacheConstants.User.BEHAVIOR, String.valueOf(userId));
        return cacheService.lRange(key, 0, limit - 1);
    }

    /**
     * 缓存用户会话信息
     */
    public void cacheUserSession(String sessionId, Map<String, Object> sessionData) {
        String key = CacheConstants.buildKey(CacheConstants.User.SESSION, sessionId);
        cacheService.hMSet(key, sessionData);
        cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
        log.debug("Cached user session: sessionId={}", sessionId);
    }

    /**
     * 获取用户会话信息
     */
    public Map<Object, Object> getUserSession(String sessionId) {
        String key = CacheConstants.buildKey(CacheConstants.User.SESSION, sessionId);
        return cacheService.hGetAll(key);
    }

    /**
     * 更新会话信息
     */
    public void updateUserSession(String sessionId, String field, Object value) {
        String key = CacheConstants.buildKey(CacheConstants.User.SESSION, sessionId);
        cacheService.hSet(key, field, value);
        cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
    }

    /**
     * 删除用户会话
     */
    public void deleteUserSession(String sessionId) {
        String key = CacheConstants.buildKey(CacheConstants.User.SESSION, sessionId);
        cacheService.delete(key);
        log.info("Deleted user session: sessionId={}", sessionId);
    }

    /**
     * 清空用户所有缓存
     */
    public void clearUserCache(Long userId) {
        String pattern = CacheConstants.User.PREFIX + CacheConstants.CACHE_KEY_SEPARATOR + "*" + 
                        CacheConstants.CACHE_KEY_SEPARATOR + userId;
        long deletedCount = cacheService.deleteByPattern(pattern);
        log.info("Cleared user cache: userId={}, deletedCount={}", userId, deletedCount);
    }

    /**
     * 批量预热用户缓存
     */
    public void warmupUserCache(List<User> users) {
        for (User user : users) {
            cacheUserInfo(user);
            
            // 初始化用户特征缓存
            Map<String, Object> defaultFeatures = new HashMap<>();
            defaultFeatures.put("last_active", System.currentTimeMillis());
            defaultFeatures.put("cache_warmed", true);
            cacheUserFeatures(user.getId(), defaultFeatures);
        }
        log.info("Warmed up user cache: count={}", users.size());
    }
}