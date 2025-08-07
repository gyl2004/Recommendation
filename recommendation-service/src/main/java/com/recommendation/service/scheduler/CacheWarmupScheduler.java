package com.recommendation.service.scheduler;

import com.recommendation.service.service.CacheService;
import com.recommendation.service.service.FallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 缓存预热调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "recommendation.cache.warmup.enabled", havingValue = "true", matchIfMissing = true)
public class CacheWarmupScheduler {
    
    private final CacheService cacheService;
    private final FallbackService fallbackService;
    
    /**
     * 定时预热热门内容缓存
     * 每15分钟执行一次
     */
    @Scheduled(fixedRate = 900000) // 15分钟
    public void warmupHotContent() {
        try {
            log.info("开始预热热门内容缓存");
            
            String[] contentTypes = {"article", "video", "product", "mixed"};
            
            for (String contentType : contentTypes) {
                try {
                    // 获取热门内容并缓存
                    var hotContent = fallbackService.getHotContentFallback(contentType, 50);
                    cacheService.cacheHotContent(contentType, hotContent);
                    
                    log.debug("预热热门内容缓存成功 - contentType: {}", contentType);
                    
                } catch (Exception e) {
                    log.error("预热热门内容缓存失败 - contentType: {}, error: {}", 
                            contentType, e.getMessage(), e);
                }
            }
            
            log.info("热门内容缓存预热完成");
            
        } catch (Exception e) {
            log.error("热门内容缓存预热异常 - error: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 定时清理过期缓存统计
     * 每小时执行一次
     */
    @Scheduled(fixedRate = 3600000) // 1小时
    public void logCacheStats() {
        try {
            CacheService.CacheStats stats = cacheService.getCacheStats();
            
            log.info("缓存统计信息 - 本地缓存命中率: {:.2f}%, Redis缓存命中率: {:.2f}%, 总请求数: {}",
                    stats.getLocalHitRate() * 100,
                    stats.getRedisHitRate() * 100,
                    stats.getTotalRequests());
            
        } catch (Exception e) {
            log.error("记录缓存统计信息失败 - error: {}", e.getMessage(), e);
        }
    }
}