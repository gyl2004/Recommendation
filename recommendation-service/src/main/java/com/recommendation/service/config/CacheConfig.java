package com.recommendation.service.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置类
 */
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Value("${recommendation.cache.user-features-ttl:3600}")
    private Integer userFeaturesTtl;
    
    @Value("${recommendation.cache.recommend-result-ttl:1800}")
    private Integer recommendResultTtl;
    
    @Value("${recommendation.cache.hot-content-ttl:7200}")
    private Integer hotContentTtl;
    
    /**
     * Caffeine本地缓存管理器
     */
    @Bean("caffeineCacheManager")
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
    
    /**
     * Redis缓存管理器
     */
    @Bean("redisCacheManager")
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(recommendResultTtl))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();
        
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
    
    /**
     * 推荐结果本地缓存
     */
    @Bean("recommendResultLocalCache")
    public Cache<String, Object> recommendResultLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
    
    /**
     * 用户特征本地缓存
     */
    @Bean("userFeaturesLocalCache")
    public Cache<String, Object> userFeaturesLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
    
    /**
     * 内容特征本地缓存
     */
    @Bean("contentFeaturesLocalCache")
    public Cache<String, Object> contentFeaturesLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(20000)
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
    
    /**
     * 热门内容本地缓存
     */
    @Bean("hotContentLocalCache")
    public Cache<String, Object> hotContentLocalCache() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .recordStats()
                .build();
    }
}