package com.recommendation.service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.recommendation.service.dto.RecommendResponse;
import com.recommendation.service.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {
    
    private final Cache<String, Object> recommendResultLocalCache;
    private final Cache<String, Object> userFeaturesLocalCache;
    private final Cache<String, Object> contentFeaturesLocalCache;
    private final Cache<String, Object> hotContentLocalCache;
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    // 缓存统计
    private final AtomicLong localCacheHits = new AtomicLong(0);
    private final AtomicLong localCacheMisses = new AtomicLong(0);
    private final AtomicLong redisCacheHits = new AtomicLong(0);
    private final AtomicLong redisCacheMisses = new AtomicLong(0);
    
    // 缓存键前缀
    private static final String RECOMMEND_RESULT_PREFIX = "recommend:result:";
    private static final String USER_FEATURES_PREFIX = "user:features:";
    private static final String CONTENT_FEATURES_PREFIX = "content:features:";
    private static final String HOT_CONTENT_PREFIX = "hot:content:";
    
    // 缓存TTL（秒）
    private static final int RECOMMEND_RESULT_TTL = 1800; // 30分钟
    private static final int USER_FEATURES_TTL = 3600;    // 1小时
    private static final int CONTENT_FEATURES_TTL = 7200; // 2小时
    private static final int HOT_CONTENT_TTL = 3600;      // 1小时
    
    @Override
    public RecommendResponse getRecommendResult(String userId, String contentType, String scene) {
        String cacheKey = buildRecommendResultKey(userId, contentType, scene);
        
        try {
            // 1. 先查本地缓存
            Object localResult = recommendResultLocalCache.getIfPresent(cacheKey);
            if (localResult != null) {
                localCacheHits.incrementAndGet();
                log.debug("命中本地缓存 - key: {}", cacheKey);
                return (RecommendResponse) localResult;
            }
            localCacheMisses.incrementAndGet();
            
            // 2. 查Redis缓存
            Object redisResult = redisTemplate.opsForValue().get(cacheKey);
            if (redisResult != null) {
                redisCacheHits.incrementAndGet();
                log.debug("命中Redis缓存 - key: {}", cacheKey);
                
                // 回写到本地缓存
                recommendResultLocalCache.put(cacheKey, redisResult);
                return (RecommendResponse) redisResult;
            }
            redisCacheMisses.incrementAndGet();
            
            log.debug("缓存未命中 - key: {}", cacheKey);
            return null;
            
        } catch (Exception e) {
            log.error("获取推荐结果缓存失败 - key: {}, error: {}", cacheKey, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public void cacheRecommendResult(String userId, String contentType, String scene, RecommendResponse response) {
        String cacheKey = buildRecommendResultKey(userId, contentType, scene);
        
        try {
            // 1. 存储到本地缓存
            recommendResultLocalCache.put(cacheKey, response);
            
            // 2. 存储到Redis缓存
            redisTemplate.opsForValue().set(cacheKey, response, RECOMMEND_RESULT_TTL, TimeUnit.SECONDS);
            
            log.debug("缓存推荐结果成功 - key: {}", cacheKey);
            
        } catch (Exception e) {
            log.error("缓存推荐结果失败 - key: {}, error: {}", cacheKey, e.getMessage(), e);
        }
    }
    
    @Override
    public <T> T getUserFeatures(String userId, Class<T> clazz) {
        String cacheKey = USER_FEATURES_PREFIX + userId;
        
        try {
            // 1. 先查本地缓存
            Object localResult = userFeaturesLocalCache.getIfPresent(cacheKey);
            if (localResult != null) {
                localCacheHits.incrementAndGet();
                return clazz.cast(localResult);
            }
            localCacheMisses.incrementAndGet();
            
            // 2. 查Redis缓存
            Object redisResult = redisTemplate.opsForValue().get(cacheKey);
            if (redisResult != null) {
                redisCacheHits.incrementAndGet();
                
                // 回写到本地缓存
                userFeaturesLocalCache.put(cacheKey, redisResult);
                return clazz.cast(redisResult);
            }
            redisCacheMisses.incrementAndGet();
            
            return null;
            
        } catch (Exception e) {
            log.error("获取用户特征缓存失败 - userId: {}, error: {}", userId, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public void cacheUserFeatures(String userId, Object features) {
        String cacheKey = USER_FEATURES_PREFIX + userId;
        
        try {
            // 1. 存储到本地缓存
            userFeaturesLocalCache.put(cacheKey, features);
            
            // 2. 存储到Redis缓存
            redisTemplate.opsForValue().set(cacheKey, features, USER_FEATURES_TTL, TimeUnit.SECONDS);
            
            log.debug("缓存用户特征成功 - userId: {}", userId);
            
        } catch (Exception e) {
            log.error("缓存用户特征失败 - userId: {}, error: {}", userId, e.getMessage(), e);
        }
    }
    
    @Override
    public <T> T getContentFeatures(String contentId, Class<T> clazz) {
        String cacheKey = CONTENT_FEATURES_PREFIX + contentId;
        
        try {
            // 1. 先查本地缓存
            Object localResult = contentFeaturesLocalCache.getIfPresent(cacheKey);
            if (localResult != null) {
                localCacheHits.incrementAndGet();
                return clazz.cast(localResult);
            }
            localCacheMisses.incrementAndGet();
            
            // 2. 查Redis缓存
            Object redisResult = redisTemplate.opsForValue().get(cacheKey);
            if (redisResult != null) {
                redisCacheHits.incrementAndGet();
                
                // 回写到本地缓存
                contentFeaturesLocalCache.put(cacheKey, redisResult);
                return clazz.cast(redisResult);
            }
            redisCacheMisses.incrementAndGet();
            
            return null;
            
        } catch (Exception e) {
            log.error("获取内容特征缓存失败 - contentId: {}, error: {}", contentId, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public void cacheContentFeatures(String contentId, Object features) {
        String cacheKey = CONTENT_FEATURES_PREFIX + contentId;
        
        try {
            // 1. 存储到本地缓存
            contentFeaturesLocalCache.put(cacheKey, features);
            
            // 2. 存储到Redis缓存
            redisTemplate.opsForValue().set(cacheKey, features, CONTENT_FEATURES_TTL, TimeUnit.SECONDS);
            
            log.debug("缓存内容特征成功 - contentId: {}", contentId);
            
        } catch (Exception e) {
            log.error("缓存内容特征失败 - contentId: {}, error: {}", contentId, e.getMessage(), e);
        }
    }
    
    @Override
    public RecommendResponse getHotContent(String contentType) {
        String cacheKey = HOT_CONTENT_PREFIX + contentType;
        
        try {
            // 1. 先查本地缓存
            Object localResult = hotContentLocalCache.getIfPresent(cacheKey);
            if (localResult != null) {
                localCacheHits.incrementAndGet();
                return (RecommendResponse) localResult;
            }
            localCacheMisses.incrementAndGet();
            
            // 2. 查Redis缓存
            Object redisResult = redisTemplate.opsForValue().get(cacheKey);
            if (redisResult != null) {
                redisCacheHits.incrementAndGet();
                
                // 回写到本地缓存
                hotContentLocalCache.put(cacheKey, redisResult);
                return (RecommendResponse) redisResult;
            }
            redisCacheMisses.incrementAndGet();
            
            return null;
            
        } catch (Exception e) {
            log.error("获取热门内容缓存失败 - contentType: {}, error: {}", contentType, e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public void cacheHotContent(String contentType, RecommendResponse response) {
        String cacheKey = HOT_CONTENT_PREFIX + contentType;
        
        try {
            // 1. 存储到本地缓存
            hotContentLocalCache.put(cacheKey, response);
            
            // 2. 存储到Redis缓存
            redisTemplate.opsForValue().set(cacheKey, response, HOT_CONTENT_TTL, TimeUnit.SECONDS);
            
            log.debug("缓存热门内容成功 - contentType: {}", contentType);
            
        } catch (Exception e) {
            log.error("缓存热门内容失败 - contentType: {}, error: {}", contentType, e.getMessage(), e);
        }
    }
    
    @Override
    public void evictUserCache(String userId) {
        try {
            // 清除本地缓存
            String userFeaturesKey = USER_FEATURES_PREFIX + userId;
            userFeaturesLocalCache.invalidate(userFeaturesKey);
            
            // 清除推荐结果缓存（模糊匹配）
            recommendResultLocalCache.asMap().keySet().removeIf(key -> key.contains(userId));
            
            // 清除Redis缓存
            redisTemplate.delete(userFeaturesKey);
            
            log.info("清除用户缓存成功 - userId: {}", userId);
            
        } catch (Exception e) {
            log.error("清除用户缓存失败 - userId: {}, error: {}", userId, e.getMessage(), e);
        }
    }
    
    @Override
    public void evictContentCache(String contentId) {
        try {
            // 清除本地缓存
            String contentFeaturesKey = CONTENT_FEATURES_PREFIX + contentId;
            contentFeaturesLocalCache.invalidate(contentFeaturesKey);
            
            // 清除Redis缓存
            redisTemplate.delete(contentFeaturesKey);
            
            log.info("清除内容缓存成功 - contentId: {}", contentId);
            
        } catch (Exception e) {
            log.error("清除内容缓存失败 - contentId: {}, error: {}", contentId, e.getMessage(), e);
        }
    }
    
    @Override
    public void warmupCache(String userId) {
        try {
            log.info("开始预热用户缓存 - userId: {}", userId);
            
            // 这里可以预加载用户特征、热门内容等
            // 实际实现中应该调用相应的服务获取数据并缓存
            
            log.info("用户缓存预热完成 - userId: {}", userId);
            
        } catch (Exception e) {
            log.error("用户缓存预热失败 - userId: {}, error: {}", userId, e.getMessage(), e);
        }
    }
    
    @Override
    public CacheStats getCacheStats() {
        CacheStats stats = new CacheStats();
        
        long localHits = localCacheHits.get();
        long localMisses = localCacheMisses.get();
        long redisHits = redisCacheHits.get();
        long redisMisses = redisCacheMisses.get();
        
        stats.setLocalCacheHits(localHits);
        stats.setLocalCacheMisses(localMisses);
        stats.setRedisCacheHits(redisHits);
        stats.setRedisCacheMisses(redisMisses);
        
        long totalLocal = localHits + localMisses;
        long totalRedis = redisHits + redisMisses;
        
        stats.setLocalHitRate(totalLocal > 0 ? (double) localHits / totalLocal : 0.0);
        stats.setRedisHitRate(totalRedis > 0 ? (double) redisHits / totalRedis : 0.0);
        stats.setTotalRequests(totalLocal + totalRedis);
        
        return stats;
    }
    
    /**
     * 构建推荐结果缓存键
     */
    private String buildRecommendResultKey(String userId, String contentType, String scene) {
        return RECOMMEND_RESULT_PREFIX + userId + ":" + contentType + ":" + scene;
    }
}