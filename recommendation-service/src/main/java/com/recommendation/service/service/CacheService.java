package com.recommendation.service.service;

import com.recommendation.service.dto.RecommendResponse;

/**
 * 缓存服务接口
 */
public interface CacheService {
    
    /**
     * 获取推荐结果缓存
     *
     * @param userId 用户ID
     * @param contentType 内容类型
     * @param scene 场景
     * @return 缓存的推荐结果
     */
    RecommendResponse getRecommendResult(String userId, String contentType, String scene);
    
    /**
     * 缓存推荐结果
     *
     * @param userId 用户ID
     * @param contentType 内容类型
     * @param scene 场景
     * @param response 推荐结果
     */
    void cacheRecommendResult(String userId, String contentType, String scene, RecommendResponse response);
    
    /**
     * 获取用户特征缓存
     *
     * @param userId 用户ID
     * @return 用户特征
     */
    <T> T getUserFeatures(String userId, Class<T> clazz);
    
    /**
     * 缓存用户特征
     *
     * @param userId 用户ID
     * @param features 用户特征
     */
    void cacheUserFeatures(String userId, Object features);
    
    /**
     * 获取内容特征缓存
     *
     * @param contentId 内容ID
     * @return 内容特征
     */
    <T> T getContentFeatures(String contentId, Class<T> clazz);
    
    /**
     * 缓存内容特征
     *
     * @param contentId 内容ID
     * @param features 内容特征
     */
    void cacheContentFeatures(String contentId, Object features);
    
    /**
     * 获取热门内容缓存
     *
     * @param contentType 内容类型
     * @return 热门内容列表
     */
    RecommendResponse getHotContent(String contentType);
    
    /**
     * 缓存热门内容
     *
     * @param contentType 内容类型
     * @param response 热门内容
     */
    void cacheHotContent(String contentType, RecommendResponse response);
    
    /**
     * 删除用户相关缓存
     *
     * @param userId 用户ID
     */
    void evictUserCache(String userId);
    
    /**
     * 删除内容相关缓存
     *
     * @param contentId 内容ID
     */
    void evictContentCache(String contentId);
    
    /**
     * 预热缓存
     *
     * @param userId 用户ID
     */
    void warmupCache(String userId);
    
    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计
     */
    CacheStats getCacheStats();
    
    /**
     * 缓存统计信息
     */
    class CacheStats {
        private long localCacheHits;
        private long localCacheMisses;
        private long redisCacheHits;
        private long redisCacheMisses;
        private double localHitRate;
        private double redisHitRate;
        private long totalRequests;
        
        // getters and setters
        public long getLocalCacheHits() { return localCacheHits; }
        public void setLocalCacheHits(long localCacheHits) { this.localCacheHits = localCacheHits; }
        
        public long getLocalCacheMisses() { return localCacheMisses; }
        public void setLocalCacheMisses(long localCacheMisses) { this.localCacheMisses = localCacheMisses; }
        
        public long getRedisCacheHits() { return redisCacheHits; }
        public void setRedisCacheHits(long redisCacheHits) { this.redisCacheHits = redisCacheHits; }
        
        public long getRedisCacheMisses() { return redisCacheMisses; }
        public void setRedisCacheMisses(long redisCacheMisses) { this.redisCacheMisses = redisCacheMisses; }
        
        public double getLocalHitRate() { return localHitRate; }
        public void setLocalHitRate(double localHitRate) { this.localHitRate = localHitRate; }
        
        public double getRedisHitRate() { return redisHitRate; }
        public void setRedisHitRate(double redisHitRate) { this.redisHitRate = redisHitRate; }
        
        public long getTotalRequests() { return totalRequests; }
        public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }
    }
}