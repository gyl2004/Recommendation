package com.recommendation.common.service;

import com.recommendation.common.constant.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 推荐缓存服务类
 * 管理推荐相关的缓存数据
 */
@Slf4j
@Service
public class RecommendationCacheService {

    @Autowired
    private CacheService cacheService;

    /**
     * 缓存推荐结果
     */
    public void cacheRecommendationResult(Long userId, String contentType, List<Long> contentIds) {
        String key = CacheConstants.buildRecommendationResultKey(userId, contentType);
        cacheService.delete(key);
        if (!contentIds.isEmpty()) {
            cacheService.rPush(key, contentIds.toArray());
            cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
        }
        log.info("Cached recommendation result: userId={}, contentType={}, count={}", 
                userId, contentType, contentIds.size());
    }

    /**
     * 获取推荐结果
     */
    public List<Object> getRecommendationResult(Long userId, String contentType, int limit) {
        String key = CacheConstants.buildRecommendationResultKey(userId, contentType);
        return cacheService.lRange(key, 0, limit - 1);
    }

    /**
     * 检查推荐结果是否存在
     */
    public boolean hasRecommendationResult(Long userId, String contentType) {
        String key = CacheConstants.buildRecommendationResultKey(userId, contentType);
        return cacheService.exists(key);
    }

    /**
     * 缓存召回结果
     */
    public void cacheRecallResult(Long userId, String algorithm, List<Long> contentIds) {
        String key = CacheConstants.buildKey(CacheConstants.Recommendation.RECALL, 
                                           String.valueOf(userId), algorithm);
        cacheService.delete(key);
        if (!contentIds.isEmpty()) {
            cacheService.rPush(key, contentIds.toArray());
            cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
        }
        log.info("Cached recall result: userId={}, algorithm={}, count={}", 
                userId, algorithm, contentIds.size());
    }

    /**
     * 获取召回结果
     */
    public List<Object> getRecallResult(Long userId, String algorithm, int limit) {
        String key = CacheConstants.buildKey(CacheConstants.Recommendation.RECALL, 
                                           String.valueOf(userId), algorithm);
        return cacheService.lRange(key, 0, limit - 1);
    }

    /**
     * 缓存排序结果
     */
    public void cacheRankResult(Long userId, String algorithm, Map<Long, Double> contentScores) {
        String key = CacheConstants.buildKey(CacheConstants.Recommendation.RANK, 
                                           String.valueOf(userId), algorithm);
        cacheService.delete(key);
        
        // 使用ZSet存储，分数为排序分数
        contentScores.forEach((contentId, score) -> 
            cacheService.zAdd(key, contentId, score));
        
        cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
        log.info("Cached rank result: userId={}, algorithm={}, count={}", 
                userId, algorithm, contentScores.size());
    }

    /**
     * 获取排序结果
     */
    public List<Object> getRankResult(Long userId, String algorithm, int limit) {
        String key = CacheConstants.buildKey(CacheConstants.Recommendation.RANK, 
                                           String.valueOf(userId), algorithm);
        return (List<Object>) cacheService.zRevRange(key, 0, limit - 1);
    }

    /**
     * 缓存推荐历史
     */
    public void cacheRecommendationHistory(Long userId, Map<String, Object> recommendationLog) {
        String key = CacheConstants.buildKey(CacheConstants.Recommendation.HISTORY, String.valueOf(userId));
        cacheService.lPush(key, recommendationLog);
        
        // 保持最近50条推荐历史
        List<Object> history = cacheService.lRange(key, 0, 49);
        cacheService.delete(key);
        if (!history.isEmpty()) {
            cacheService.rPush(key, history.toArray());
        }
        
        cacheService.expire(key, CacheConstants.LONG_EXPIRE_TIME);
        log.debug("Cached recommendation history: userId={}", userId);
    }

    /**
     * 获取推荐历史
     */
    public List<Object> getRecommendationHistory(Long userId, int limit) {
        String key = CacheConstants.buildKey(CacheConstants.Recommendation.HISTORY, String.valueOf(userId));
        return cacheService.lRange(key, 0, limit - 1);
    }

    /**
     * 缓存算法模型
     */
    public void cacheAlgorithmModel(String algorithmType, Object modelData) {
        String key = CacheConstants.buildKey(CacheConstants.Recommendation.MODEL, algorithmType);
        cacheService.set(key, modelData, CacheConstants.LONG_EXPIRE_TIME);
        log.info("Cached algorithm model: algorithmType={}", algorithmType);
    }

    /**
     * 获取算法模型
     */
    public Object getAlgorithmModel(String algorithmType) {
        String key = CacheConstants.buildKey(CacheConstants.Recommendation.MODEL, algorithmType);
        return cacheService.get(key);
    }

    /**
     * 删除算法模型缓存
     */
    public void deleteAlgorithmModel(String algorithmType) {
        String key = CacheConstants.buildKey(CacheConstants.Recommendation.MODEL, algorithmType);
        cacheService.delete(key);
        log.info("Deleted algorithm model cache: algorithmType={}", algorithmType);
    }

    /**
     * 缓存用户推荐上下文
     */
    public void cacheRecommendationContext(Long userId, Map<String, Object> context) {
        String key = CacheConstants.buildKey("recommend:context", String.valueOf(userId));
        cacheService.hMSet(key, context);
        cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
        log.debug("Cached recommendation context: userId={}", userId);
    }

    /**
     * 获取用户推荐上下文
     */
    public Map<Object, Object> getRecommendationContext(Long userId) {
        String key = CacheConstants.buildKey("recommend:context", String.valueOf(userId));
        return cacheService.hGetAll(key);
    }

    /**
     * 更新推荐上下文
     */
    public void updateRecommendationContext(Long userId, String field, Object value) {
        String key = CacheConstants.buildKey("recommend:context", String.valueOf(userId));
        cacheService.hSet(key, field, value);
        cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
    }

    /**
     * 缓存推荐候选池
     */
    public void cacheRecommendationPool(String poolKey, List<Long> contentIds, long expireTime) {
        String key = CacheConstants.buildKey("recommend:pool", poolKey);
        cacheService.delete(key);
        if (!contentIds.isEmpty()) {
            cacheService.sAdd(key, contentIds.toArray());
            cacheService.expire(key, expireTime);
        }
        log.info("Cached recommendation pool: poolKey={}, count={}", poolKey, contentIds.size());
    }

    /**
     * 获取推荐候选池
     */
    public List<Object> getRecommendationPool(String poolKey) {
        String key = CacheConstants.buildKey("recommend:pool", poolKey);
        return (List<Object>) cacheService.sMembers(key);
    }

    /**
     * 记录推荐曝光
     */
    public void recordRecommendationExposure(Long userId, Long contentId) {
        String key = CacheConstants.buildKey("recommend:exposure", String.valueOf(userId));
        cacheService.sAdd(key, contentId);
        cacheService.expire(key, CacheConstants.LONG_EXPIRE_TIME);
        log.debug("Recorded recommendation exposure: userId={}, contentId={}", userId, contentId);
    }

    /**
     * 检查内容是否已曝光
     */
    public boolean isContentExposed(Long userId, Long contentId) {
        String key = CacheConstants.buildKey("recommend:exposure", String.valueOf(userId));
        return cacheService.sMembers(key).contains(contentId);
    }

    /**
     * 缓存推荐性能指标
     */
    public void cacheRecommendationMetrics(String metricsKey, Map<String, Object> metrics) {
        String key = CacheConstants.buildKey("recommend:metrics", metricsKey);
        cacheService.hMSet(key, metrics);
        cacheService.expire(key, CacheConstants.DEFAULT_EXPIRE_TIME);
        log.debug("Cached recommendation metrics: metricsKey={}", metricsKey);
    }

    /**
     * 获取推荐性能指标
     */
    public Map<Object, Object> getRecommendationMetrics(String metricsKey) {
        String key = CacheConstants.buildKey("recommend:metrics", metricsKey);
        return cacheService.hGetAll(key);
    }

    /**
     * 增加推荐指标计数
     */
    public long incrementRecommendationCounter(String counterKey) {
        String key = CacheConstants.buildKey("recommend:counter", counterKey);
        long count = cacheService.increment(key);
        cacheService.expire(key, CacheConstants.LONG_EXPIRE_TIME);
        return count;
    }

    /**
     * 清空用户推荐缓存
     */
    public void clearUserRecommendationCache(Long userId) {
        String pattern = CacheConstants.Recommendation.PREFIX + CacheConstants.CACHE_KEY_SEPARATOR + "*" + 
                        CacheConstants.CACHE_KEY_SEPARATOR + userId + CacheConstants.CACHE_KEY_SEPARATOR + "*";
        long deletedCount = cacheService.deleteByPattern(pattern);
        log.info("Cleared user recommendation cache: userId={}, deletedCount={}", userId, deletedCount);
    }

    /**
     * 清空所有推荐缓存
     */
    public void clearAllRecommendationCache() {
        String pattern = CacheConstants.Recommendation.PREFIX + CacheConstants.CACHE_KEY_SEPARATOR + "*";
        long deletedCount = cacheService.deleteByPattern(pattern);
        log.info("Cleared all recommendation cache: deletedCount={}", deletedCount);
    }

    /**
     * 预热推荐缓存
     */
    public void warmupRecommendationCache(List<Long> userIds) {
        for (Long userId : userIds) {
            // 初始化推荐上下文
            Map<String, Object> context = new HashMap<>();
            context.put("last_request_time", System.currentTimeMillis());
            context.put("request_count", 0);
            context.put("cache_warmed", true);
            cacheRecommendationContext(userId, context);
        }
        log.info("Warmed up recommendation cache: userCount={}", userIds.size());
    }
}