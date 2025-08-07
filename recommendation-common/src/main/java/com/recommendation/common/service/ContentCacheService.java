package com.recommendation.common.service;

import com.recommendation.common.constant.CacheConstants;
import com.recommendation.common.entity.Content;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内容缓存服务类
 * 管理内容相关的缓存数据
 */
@Slf4j
@Service
public class ContentCacheService {

    @Autowired
    private CacheService cacheService;

    /**
     * 缓存内容基础信息
     */
    public void cacheContentInfo(Content content) {
        String key = CacheConstants.buildKey(CacheConstants.Content.INFO, String.valueOf(content.getId()));
        cacheService.set(key, content, CacheConstants.LONG_EXPIRE_TIME);
        log.info("Cached content info: contentId={}", content.getId());
    }

    /**
     * 获取内容基础信息
     */
    public Content getContentInfo(Long contentId) {
        String key = CacheConstants.buildKey(CacheConstants.Content.INFO, String.valueOf(contentId));
        return cacheService.get(key, Content.class);
    }

    /**
     * 删除内容基础信息缓存
     */
    public void deleteContentInfo(Long contentId) {
        String key = CacheConstants.buildKey(CacheConstants.Content.INFO, String.valueOf(contentId));
        cacheService.delete(key);
        log.info("Deleted content info cache: contentId={}", contentId);
    }

    /**
     * 缓存内容特征数据
     */
    public void cacheContentFeatures(Long contentId, Map<String, Object> features) {
        String key = CacheConstants.buildContentFeaturesKey(contentId);
        cacheService.hMSet(key, features);
        cacheService.expire(key, CacheConstants.LONG_EXPIRE_TIME);
        log.info("Cached content features: contentId={}, featuresCount={}", contentId, features.size());
    }

    /**
     * 获取内容特征数据
     */
    public Map<Object, Object> getContentFeatures(Long contentId) {
        String key = CacheConstants.buildContentFeaturesKey(contentId);
        return cacheService.hGetAll(key);
    }

    /**
     * 获取内容特定特征
     */
    public Object getContentFeature(Long contentId, String featureName) {
        String key = CacheConstants.buildContentFeaturesKey(contentId);
        return cacheService.hGet(key, featureName);
    }

    /**
     * 更新内容特征
     */
    public void updateContentFeature(Long contentId, String featureName, Object featureValue) {
        String key = CacheConstants.buildContentFeaturesKey(contentId);
        cacheService.hSet(key, featureName, featureValue);
        log.debug("Updated content feature: contentId={}, feature={}", contentId, featureName);
    }

    /**
     * 缓存热门内容列表
     */
    public void cacheHotContents(String contentType, List<Long> contentIds) {
        String key = CacheConstants.buildHotContentKey(contentType);
        // 使用ZSet存储，分数为热度值
        cacheService.delete(key);
        for (int i = 0; i < contentIds.size(); i++) {
            cacheService.zAdd(key, contentIds.get(i), contentIds.size() - i);
        }
        cacheService.expire(key, CacheConstants.DEFAULT_EXPIRE_TIME);
        log.info("Cached hot contents: contentType={}, count={}", contentType, contentIds.size());
    }

    /**
     * 获取热门内容列表
     */
    public Set<Object> getHotContents(String contentType, int limit) {
        String key = CacheConstants.buildHotContentKey(contentType);
        return cacheService.zRevRange(key, 0, limit - 1);
    }

    /**
     * 添加热门内容
     */
    public void addHotContent(String contentType, Long contentId, double hotScore) {
        String key = CacheConstants.buildHotContentKey(contentType);
        cacheService.zAdd(key, contentId, hotScore);
        log.debug("Added hot content: contentType={}, contentId={}, score={}", 
                 contentType, contentId, hotScore);
    }

    /**
     * 缓存最新内容列表
     */
    public void cacheLatestContents(String contentType, List<Long> contentIds) {
        String key = CacheConstants.buildKey(CacheConstants.Content.LATEST, contentType);
        cacheService.delete(key);
        if (!contentIds.isEmpty()) {
            cacheService.rPush(key, contentIds.toArray());
            cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
        }
        log.info("Cached latest contents: contentType={}, count={}", contentType, contentIds.size());
    }

    /**
     * 获取最新内容列表
     */
    public List<Object> getLatestContents(String contentType, int limit) {
        String key = CacheConstants.buildKey(CacheConstants.Content.LATEST, contentType);
        return cacheService.lRange(key, 0, limit - 1);
    }

    /**
     * 缓存分类内容列表
     */
    public void cacheCategoryContents(Integer categoryId, List<Long> contentIds) {
        String key = CacheConstants.buildKey(CacheConstants.Content.CATEGORY, String.valueOf(categoryId));
        cacheService.delete(key);
        if (!contentIds.isEmpty()) {
            cacheService.rPush(key, contentIds.toArray());
            cacheService.expire(key, CacheConstants.DEFAULT_EXPIRE_TIME);
        }
        log.info("Cached category contents: categoryId={}, count={}", categoryId, contentIds.size());
    }

    /**
     * 获取分类内容列表
     */
    public List<Object> getCategoryContents(Integer categoryId, int limit) {
        String key = CacheConstants.buildKey(CacheConstants.Content.CATEGORY, String.valueOf(categoryId));
        return cacheService.lRange(key, 0, limit - 1);
    }

    /**
     * 缓存内容统计信息
     */
    public void cacheContentStats(Long contentId, Map<String, Object> stats) {
        String key = CacheConstants.buildKey(CacheConstants.Content.STATS, String.valueOf(contentId));
        cacheService.hMSet(key, stats);
        cacheService.expire(key, CacheConstants.SHORT_EXPIRE_TIME);
        log.debug("Cached content stats: contentId={}", contentId);
    }

    /**
     * 获取内容统计信息
     */
    public Map<Object, Object> getContentStats(Long contentId) {
        String key = CacheConstants.buildKey(CacheConstants.Content.STATS, String.valueOf(contentId));
        return cacheService.hGetAll(key);
    }

    /**
     * 增加内容浏览次数
     */
    public long incrementViewCount(Long contentId) {
        String key = CacheConstants.buildKey(CacheConstants.Content.STATS, String.valueOf(contentId));
        long count = cacheService.increment(key + ":view_count");
        cacheService.expire(key + ":view_count", CacheConstants.SHORT_EXPIRE_TIME);
        log.debug("Incremented view count: contentId={}, count={}", contentId, count);
        return count;
    }

    /**
     * 增加内容点赞次数
     */
    public long incrementLikeCount(Long contentId) {
        String key = CacheConstants.buildKey(CacheConstants.Content.STATS, String.valueOf(contentId));
        long count = cacheService.increment(key + ":like_count");
        cacheService.expire(key + ":like_count", CacheConstants.SHORT_EXPIRE_TIME);
        log.debug("Incremented like count: contentId={}, count={}", contentId, count);
        return count;
    }

    /**
     * 增加内容分享次数
     */
    public long incrementShareCount(Long contentId) {
        String key = CacheConstants.buildKey(CacheConstants.Content.STATS, String.valueOf(contentId));
        long count = cacheService.increment(key + ":share_count");
        cacheService.expire(key + ":share_count", CacheConstants.SHORT_EXPIRE_TIME);
        log.debug("Incremented share count: contentId={}, count={}", contentId, count);
        return count;
    }

    /**
     * 获取内容浏览次数
     */
    public long getViewCount(Long contentId) {
        String key = CacheConstants.buildKey(CacheConstants.Content.STATS, String.valueOf(contentId)) + ":view_count";
        Object count = cacheService.get(key);
        return count instanceof Number ? ((Number) count).longValue() : 0L;
    }

    /**
     * 批量获取内容信息
     */
    public Map<Long, Content> batchGetContentInfo(List<Long> contentIds) {
        Map<Long, Content> result = new HashMap<>();
        for (Long contentId : contentIds) {
            Content content = getContentInfo(contentId);
            if (content != null) {
                result.put(contentId, content);
            }
        }
        log.debug("Batch get content info: requested={}, found={}", contentIds.size(), result.size());
        return result;
    }

    /**
     * 批量缓存内容信息
     */
    public void batchCacheContentInfo(List<Content> contents) {
        for (Content content : contents) {
            cacheContentInfo(content);
        }
        log.info("Batch cached content info: count={}", contents.size());
    }

    /**
     * 清空内容相关缓存
     */
    public void clearContentCache(Long contentId) {
        String pattern = CacheConstants.Content.PREFIX + CacheConstants.CACHE_KEY_SEPARATOR + "*" + 
                        CacheConstants.CACHE_KEY_SEPARATOR + contentId;
        long deletedCount = cacheService.deleteByPattern(pattern);
        log.info("Cleared content cache: contentId={}, deletedCount={}", contentId, deletedCount);
    }

    /**
     * 预热内容缓存
     */
    public void warmupContentCache(List<Content> contents) {
        for (Content content : contents) {
            cacheContentInfo(content);
            
            // 初始化内容特征缓存
            Map<String, Object> defaultFeatures = new HashMap<>();
            defaultFeatures.put("content_type", content.getContentType().getCode());
            defaultFeatures.put("category_id", content.getCategoryId());
            defaultFeatures.put("hot_score", content.getHotScore());
            defaultFeatures.put("cache_warmed", true);
            cacheContentFeatures(content.getId(), defaultFeatures);
            
            // 初始化统计信息缓存
            Map<String, Object> stats = new HashMap<>();
            stats.put("view_count", content.getViewCount());
            stats.put("like_count", content.getLikeCount());
            stats.put("share_count", content.getShareCount());
            stats.put("comment_count", content.getCommentCount());
            cacheContentStats(content.getId(), stats);
        }
        log.info("Warmed up content cache: count={}", contents.size());
    }

    /**
     * 刷新热门内容缓存
     */
    public void refreshHotContentsCache() {
        // 清空所有内容类型的热门缓存
        String pattern = CacheConstants.Content.HOT + CacheConstants.CACHE_KEY_SEPARATOR + "*";
        long deletedCount = cacheService.deleteByPattern(pattern);
        log.info("Refreshed hot contents cache: deletedCount={}", deletedCount);
    }
}