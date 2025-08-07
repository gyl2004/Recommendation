package com.recommendation.service.service.impl;

import com.recommendation.service.dto.RecommendRequest;
import com.recommendation.service.dto.RecommendResponse;
import com.recommendation.service.service.FallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 降级服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FallbackServiceImpl implements FallbackService {
    
    @Value("${recommendation.fallback.hot-content-size:50}")
    private Integer hotContentSize;
    
    @Override
    public RecommendResponse getFallbackRecommendation(RecommendRequest request, String requestId) {
        log.info("执行降级策略 - userId: {}, requestId: {}", request.getUserId(), requestId);
        
        try {
            // 优先返回热门内容
            RecommendResponse hotContentResponse = getHotContentFallback(
                    request.getContentType(), request.getSize());
            
            if (hotContentResponse.getItems() != null && !hotContentResponse.getItems().isEmpty()) {
                hotContentResponse.setRequestId(requestId);
                hotContentResponse.setFromCache(false);
                
                Map<String, Object> extraInfo = new HashMap<>();
                extraInfo.put("fallbackType", "hot_content");
                extraInfo.put("timestamp", System.currentTimeMillis());
                hotContentResponse.setExtraInfo(extraInfo);
                
                return hotContentResponse;
            }
            
            // 热门内容也不可用时，返回默认推荐
            return getDefaultRecommendation(request.getContentType(), request.getSize());
            
        } catch (Exception e) {
            log.error("降级策略执行失败 - userId: {}, requestId: {}, error: {}", 
                    request.getUserId(), requestId, e.getMessage(), e);
            
            // 最后的兜底方案
            return getDefaultRecommendation(request.getContentType(), request.getSize());
        }
    }
    
    @Override
    public RecommendResponse getHotContentFallback(String contentType, Integer size) {
        try {
            log.info("获取热门内容作为降级方案 - contentType: {}, size: {}", contentType, size);
            
            List<RecommendResponse.RecommendItem> hotItems = new ArrayList<>();
            
            // 模拟热门内容数据（实际应该从缓存或数据库获取）
            for (int i = 1; i <= Math.min(size, hotContentSize); i++) {
                RecommendResponse.RecommendItem item = RecommendResponse.RecommendItem.builder()
                        .contentId("hot_" + contentType + "_" + i)
                        .contentType(contentType)
                        .title(getHotContentTitle(contentType, i))
                        .description("这是一个热门的" + getContentTypeName(contentType) + "内容")
                        .coverUrl("https://example.com/cover/" + i + ".jpg")
                        .author("热门作者" + i)
                        .publishTime(System.currentTimeMillis() - i * 3600000L) // i小时前
                        .score(100.0 - i) // 热度递减
                        .reason("热门推荐")
                        .confidence(0.8)
                        .tags(Arrays.asList("热门", getContentTypeName(contentType)))
                        .extraData(new HashMap<>())
                        .build();
                
                hotItems.add(item);
            }
            
            return RecommendResponse.builder()
                    .items(hotItems)
                    .total(hotItems.size())
                    .algorithmVersion("fallback_v1.0")
                    .fromCache(false)
                    .build();
            
        } catch (Exception e) {
            log.error("获取热门内容失败 - contentType: {}, error: {}", contentType, e.getMessage(), e);
            return getDefaultRecommendation(contentType, size);
        }
    }
    
    @Override
    public RecommendResponse getDefaultRecommendation(String contentType, Integer size) {
        log.info("返回默认推荐内容 - contentType: {}, size: {}", contentType, size);
        
        List<RecommendResponse.RecommendItem> defaultItems = new ArrayList<>();
        
        // 创建默认推荐内容
        for (int i = 1; i <= Math.min(size, 10); i++) {
            RecommendResponse.RecommendItem item = RecommendResponse.RecommendItem.builder()
                    .contentId("default_" + contentType + "_" + i)
                    .contentType(contentType)
                    .title(getDefaultContentTitle(contentType, i))
                    .description("这是一个默认的" + getContentTypeName(contentType) + "内容")
                    .coverUrl("https://example.com/default/" + i + ".jpg")
                    .author("默认作者" + i)
                    .publishTime(System.currentTimeMillis() - i * 7200000L) // i*2小时前
                    .score(50.0)
                    .reason("默认推荐")
                    .confidence(0.5)
                    .tags(Arrays.asList("默认", getContentTypeName(contentType)))
                    .extraData(new HashMap<>())
                    .build();
            
            defaultItems.add(item);
        }
        
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("fallbackType", "default");
        extraInfo.put("timestamp", System.currentTimeMillis());
        
        return RecommendResponse.builder()
                .items(defaultItems)
                .total(defaultItems.size())
                .algorithmVersion("default_v1.0")
                .fromCache(false)
                .extraInfo(extraInfo)
                .build();
    }
    
    /**
     * 获取热门内容标题
     */
    private String getHotContentTitle(String contentType, int index) {
        switch (contentType) {
            case "article":
                return "热门文章标题 " + index;
            case "video":
                return "热门视频标题 " + index;
            case "product":
                return "热门商品标题 " + index;
            default:
                return "热门内容标题 " + index;
        }
    }
    
    /**
     * 获取默认内容标题
     */
    private String getDefaultContentTitle(String contentType, int index) {
        switch (contentType) {
            case "article":
                return "推荐文章标题 " + index;
            case "video":
                return "推荐视频标题 " + index;
            case "product":
                return "推荐商品标题 " + index;
            default:
                return "推荐内容标题 " + index;
        }
    }
    
    /**
     * 获取内容类型中文名称
     */
    private String getContentTypeName(String contentType) {
        switch (contentType) {
            case "article":
                return "文章";
            case "video":
                return "视频";
            case "product":
                return "商品";
            default:
                return "内容";
        }
    }

    // ========== 多级降级策略实现 ==========

    @Override
    public RecommendResponse getCircuitBreakerFallback(String userId, String contentType, Integer size) {
        log.warn("熔断器开启，执行熔断降级策略 - userId: {}, contentType: {}", userId, contentType);
        
        try {
            // 熔断时优先返回用户历史偏好的热门内容
            List<RecommendResponse.RecommendItem> items = new ArrayList<>();
            
            for (int i = 1; i <= Math.min(size, 20); i++) {
                RecommendResponse.RecommendItem item = RecommendResponse.RecommendItem.builder()
                        .contentId("circuit_" + contentType + "_" + i)
                        .contentType(contentType)
                        .title("熔断降级-" + getContentTypeName(contentType) + "内容 " + i)
                        .description("系统繁忙，为您推荐热门内容")
                        .coverUrl("https://example.com/circuit/" + i + ".jpg")
                        .author("系统推荐")
                        .publishTime(System.currentTimeMillis() - i * 1800000L)
                        .score(90.0 - i)
                        .reason("系统繁忙-热门推荐")
                        .confidence(0.7)
                        .tags(Arrays.asList("热门", "系统推荐"))
                        .extraData(new HashMap<>())
                        .build();
                
                items.add(item);
            }
            
            Map<String, Object> extraInfo = new HashMap<>();
            extraInfo.put("fallbackType", "circuit_breaker");
            extraInfo.put("fallbackReason", "服务熔断");
            extraInfo.put("timestamp", System.currentTimeMillis());
            
            return RecommendResponse.builder()
                    .items(items)
                    .total(items.size())
                    .algorithmVersion("circuit_fallback_v1.0")
                    .fromCache(false)
                    .extraInfo(extraInfo)
                    .build();
                    
        } catch (Exception e) {
            log.error("熔断降级策略执行失败 - userId: {}, error: {}", userId, e.getMessage(), e);
            return getDefaultFallback(userId, contentType, size);
        }
    }

    @Override
    public RecommendResponse getTimeoutFallback(String userId, String contentType, Integer size) {
        log.warn("服务超时，执行超时降级策略 - userId: {}, contentType: {}", userId, contentType);
        
        try {
            // 超时时返回缓存的推荐结果或简化推荐
            List<RecommendResponse.RecommendItem> items = new ArrayList<>();
            
            for (int i = 1; i <= Math.min(size, 15); i++) {
                RecommendResponse.RecommendItem item = RecommendResponse.RecommendItem.builder()
                        .contentId("timeout_" + contentType + "_" + i)
                        .contentType(contentType)
                        .title("快速推荐-" + getContentTypeName(contentType) + "内容 " + i)
                        .description("为您快速推荐优质内容")
                        .coverUrl("https://example.com/timeout/" + i + ".jpg")
                        .author("快速推荐")
                        .publishTime(System.currentTimeMillis() - i * 3600000L)
                        .score(80.0 - i)
                        .reason("快速推荐")
                        .confidence(0.6)
                        .tags(Arrays.asList("快速", "优质"))
                        .extraData(new HashMap<>())
                        .build();
                
                items.add(item);
            }
            
            Map<String, Object> extraInfo = new HashMap<>();
            extraInfo.put("fallbackType", "timeout");
            extraInfo.put("fallbackReason", "服务超时");
            extraInfo.put("timestamp", System.currentTimeMillis());
            
            return RecommendResponse.builder()
                    .items(items)
                    .total(items.size())
                    .algorithmVersion("timeout_fallback_v1.0")
                    .fromCache(true)
                    .extraInfo(extraInfo)
                    .build();
                    
        } catch (Exception e) {
            log.error("超时降级策略执行失败 - userId: {}, error: {}", userId, e.getMessage(), e);
            return getDefaultFallback(userId, contentType, size);
        }
    }

    @Override
    public RecommendResponse getDefaultFallback(String userId, String contentType, Integer size) {
        log.warn("执行默认降级策略 - userId: {}, contentType: {}", userId, contentType);
        
        // 复用原有的默认推荐逻辑，但添加降级标识
        RecommendResponse response = getDefaultRecommendation(contentType, size);
        
        Map<String, Object> extraInfo = response.getExtraInfo();
        if (extraInfo == null) {
            extraInfo = new HashMap<>();
        }
        extraInfo.put("fallbackType", "default_fallback");
        extraInfo.put("fallbackReason", "其他异常");
        extraInfo.put("userId", userId);
        response.setExtraInfo(extraInfo);
        
        return response;
    }

    @Override
    public List<Long> getRecallFallback(String userId, String contentType, Integer size) {
        log.warn("召回服务降级 - userId: {}, contentType: {}", userId, contentType);
        
        try {
            List<Long> fallbackIds = new ArrayList<>();
            
            // 返回预设的热门内容ID列表
            long baseId = getContentTypeBaseId(contentType);
            for (int i = 0; i < Math.min(size * 3, 100); i++) { // 召回数量通常是最终推荐数量的3倍
                fallbackIds.add(baseId + i);
            }
            
            log.info("召回服务降级完成 - userId: {}, 返回{}个候选内容", userId, fallbackIds.size());
            return fallbackIds;
            
        } catch (Exception e) {
            log.error("召回服务降级失败 - userId: {}, error: {}", userId, e.getMessage(), e);
            
            // 最简单的兜底方案
            List<Long> emergencyIds = new ArrayList<>();
            long baseId = 1000L;
            for (int i = 0; i < Math.min(size, 10); i++) {
                emergencyIds.add(baseId + i);
            }
            return emergencyIds;
        }
    }

    @Override
    public List<Long> getRankingFallback(List<Long> candidates, String userId) {
        log.warn("排序服务降级 - userId: {}, 候选数量: {}", userId, candidates.size());
        
        try {
            if (candidates == null || candidates.isEmpty()) {
                return new ArrayList<>();
            }
            
            // 简单的降级排序策略：按ID倒序排列（假设ID越大越新）
            List<Long> sortedCandidates = new ArrayList<>(candidates);
            sortedCandidates.sort((a, b) -> Long.compare(b, a));
            
            log.info("排序服务降级完成 - userId: {}, 排序后数量: {}", userId, sortedCandidates.size());
            return sortedCandidates;
            
        } catch (Exception e) {
            log.error("排序服务降级失败 - userId: {}, error: {}", userId, e.getMessage(), e);
            
            // 异常时直接返回原始候选列表
            return candidates != null ? new ArrayList<>(candidates) : new ArrayList<>();
        }
    }

    /**
     * 根据内容类型获取基础ID
     */
    private long getContentTypeBaseId(String contentType) {
        switch (contentType) {
            case "article":
                return 10000L;
            case "video":
                return 20000L;
            case "product":
                return 30000L;
            default:
                return 1000L;
        }
    }
}