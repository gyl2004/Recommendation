package com.recommendation.service.recall.impl;

import com.recommendation.common.dto.RecommendRequest;
import com.recommendation.common.entity.Content;
import com.recommendation.common.repository.ContentRepository;
import com.recommendation.common.repository.UserBehaviorRepository;
import com.recommendation.service.recall.RecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 热门内容召回服务
 * 基于内容热度分数和用户行为统计进行召回
 * 支持需求5.1, 5.2: 推荐算法引擎 - 热门内容召回策略
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotContentRecallService implements RecallService {
    
    private final ContentRepository contentRepository;
    private final UserBehaviorRepository userBehaviorRepository;
    
    private static final double WEIGHT = 0.2;
    private static final int HOT_CONTENT_LIMIT = 200;
    
    @Override
    public List<Content> multiPathRecall(RecommendRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            Long userId = Long.parseLong(request.getUserId());
            
            // 1. 获取用户已浏览的内容，避免重复推荐
            Set<Long> viewedContentIds = getViewedContentIds(userId);
            
            // 2. 获取全局热门内容
            List<Content> globalHotContents = getGlobalHotContents(request);
            
            // 3. 获取基于用户行为的热门内容
            List<Content> behaviorHotContents = getBehaviorBasedHotContents(request);
            
            // 4. 合并和去重
            List<Content> mergedContents = mergeAndDeduplicateContents(
                globalHotContents, behaviorHotContents, viewedContentIds);
            
            // 5. 按热度分数排序并限制数量
            List<Content> finalContents = mergedContents.stream()
                .sorted((c1, c2) -> c2.getHotScore().compareTo(c1.getHotScore()))
                .limit(request.getSize())
                .collect(Collectors.toList());
            
            log.info("Hot content recall completed for user: {}, recalled: {} contents, duration: {}ms", 
                userId, finalContents.size(), System.currentTimeMillis() - startTime);
            
            return finalContents;
            
        } catch (Exception e) {
            log.error("Error in hot content recall for user: {}", request.getUserId(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户已浏览的内容ID集合
     */
    private Set<Long> getViewedContentIds(Long userId) {
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        List<Long> viewedIds = userBehaviorRepository.findRecentViewedContentIds(userId, oneWeekAgo);
        return new HashSet<>(viewedIds);
    }
    
    /**
     * 获取全局热门内容
     */
    private List<Content> getGlobalHotContents(RecommendRequest request) {
        Pageable pageable = PageRequest.of(0, HOT_CONTENT_LIMIT);
        List<Content> hotContents = contentRepository.findHotContents(pageable).getContent();
        
        return hotContents.stream()
            .filter(content -> matchesContentTypeFilter(content, request.getContentType()))
            .filter(content -> matchesCategoryFilter(content, request.getCategoryId()))
            .collect(Collectors.toList());
    }
    
    /**
     * 获取基于用户行为的热门内容
     */
    private List<Content> getBehaviorBasedHotContents(RecommendRequest request) {
        // 获取最近7天的热门内容(基于用户行为统计)
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);
        Pageable pageable = PageRequest.of(0, HOT_CONTENT_LIMIT);
        
        List<Object[]> hotContentData = userBehaviorRepository
            .findHotContentsByBehavior(oneWeekAgo, pageable);
        
        if (hotContentData.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 提取内容ID
        List<Long> contentIds = hotContentData.stream()
            .map(data -> (Long) data[0])
            .collect(Collectors.toList());
        
        // 查询内容详情
        List<Content> contents = contentRepository.findAllById(contentIds);
        
        return contents.stream()
            .filter(Content::isPublished)
            .filter(content -> matchesContentTypeFilter(content, request.getContentType()))
            .filter(content -> matchesCategoryFilter(content, request.getCategoryId()))
            .collect(Collectors.toList());
    }
    
    /**
     * 合并和去重内容列表
     */
    private List<Content> mergeAndDeduplicateContents(List<Content> globalHot, 
                                                     List<Content> behaviorHot, 
                                                     Set<Long> viewedContentIds) {
        
        Map<Long, Content> contentMap = new LinkedHashMap<>();
        
        // 先添加全局热门内容
        for (Content content : globalHot) {
            if (!viewedContentIds.contains(content.getId())) {
                contentMap.put(content.getId(), content);
            }
        }
        
        // 再添加行为热门内容(如果不存在的话)
        for (Content content : behaviorHot) {
            if (!viewedContentIds.contains(content.getId()) && 
                !contentMap.containsKey(content.getId())) {
                contentMap.put(content.getId(), content);
            }
        }
        
        return new ArrayList<>(contentMap.values());
    }
    
    /**
     * 检查内容类型是否匹配过滤条件
     */
    private boolean matchesContentTypeFilter(Content content, String contentTypeFilter) {
        if ("mixed".equals(contentTypeFilter)) {
            return true;
        }
        
        return content.getContentType().getCode().equals(contentTypeFilter);
    }
    
    /**
     * 检查分类是否匹配过滤条件
     */
    private boolean matchesCategoryFilter(Content content, Integer categoryId) {
        if (categoryId == null) {
            return true;
        }
        
        return Objects.equals(content.getCategoryId(), categoryId);
    }
    
    @Override
    public String getAlgorithmName() {
        return "hot_content";
    }
    
    @Override
    public double getWeight() {
        return WEIGHT;
    }
}