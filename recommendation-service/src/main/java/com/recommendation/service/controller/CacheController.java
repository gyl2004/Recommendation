package com.recommendation.service.controller;

import com.recommendation.service.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;

/**
 * 缓存管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/cache")
@RequiredArgsConstructor
public class CacheController {
    
    private final CacheService cacheService;
    
    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<CacheService.CacheStats> getCacheStats() {
        try {
            CacheService.CacheStats stats = cacheService.getCacheStats();
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("获取缓存统计信息失败 - error: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 清除用户缓存
     */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> evictUserCache(@PathVariable @NotBlank String userId) {
        try {
            cacheService.evictUserCache(userId);
            log.info("清除用户缓存成功 - userId: {}", userId);
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("清除用户缓存失败 - userId: {}, error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 清除内容缓存
     */
    @DeleteMapping("/content/{contentId}")
    public ResponseEntity<Void> evictContentCache(@PathVariable @NotBlank String contentId) {
        try {
            cacheService.evictContentCache(contentId);
            log.info("清除内容缓存成功 - contentId: {}", contentId);
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("清除内容缓存失败 - contentId: {}, error: {}", contentId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 预热用户缓存
     */
    @PostMapping("/warmup/{userId}")
    public ResponseEntity<Void> warmupUserCache(@PathVariable @NotBlank String userId) {
        try {
            cacheService.warmupCache(userId);
            log.info("预热用户缓存成功 - userId: {}", userId);
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("预热用户缓存失败 - userId: {}, error: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
}