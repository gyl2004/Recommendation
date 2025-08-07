package com.recommendation.service.recall.impl;

import com.recommendation.common.dto.RecommendRequest;
import com.recommendation.common.entity.Content;
import com.recommendation.common.entity.UserBehavior;
import com.recommendation.common.repository.ContentRepository;
import com.recommendation.common.repository.UserBehaviorRepository;
import com.recommendation.service.recall.RecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户历史行为召回服务
 * 基于用户历史偏好进行召回
 * 支持需求5.1, 5.2: 推荐算法引擎 - 用户历史行为召回策略
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserHistoryRecallService implements RecallService {
    
    private final UserBehaviorRepository userBehaviorRepository;
    private final ContentRepository contentRepository;
    
    private static final double WEIGHT = 0.25;
    private static final int HISTORY_DAYS = 30;
    private static final int MAX_CATEGORY_CONTENTS = 20;
    
    @Override
    public List<Content> multiPathRecall(RecommendRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            Long userId = Long.parseLong(request.getUserId());
            
            // 1. 获取用户已浏览的内容，避免重复推荐
            Set<Long> viewedContentIds = getViewedContentIds(userId);
            
            // 2. 分析用户历史偏好
            Map<Integer, Double> categoryPreferences = analyzeCategoryPreferences(userId);
            if (categoryPreferences.isEmpty()) {
                log.info("No category preferences found for user: {}", userId);
                return Collections.emptyList();
            }
            
            // 3. 基于偏好分类召回内容
            List<Content> recalledContents = recallContentsByPreferences(
                categoryPreferences, viewedContentIds, request);
            
            log.info("User history recall completed for user: {}, recalled: {} contents, duration: {}ms", 
                userId, recalledContents.size(), System.currentTimeMillis() - startTime);
            
            return recalledContents;
            
        } catch (Exception e) {
            log.error("Error in user history recall for user: {}", request.getUserId(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户已浏览的内容ID集合
     */
    private Set<Long> getViewedContentIds(Long userId) {
        LocalDateTime oneMonthAgo = LocalDateTime.now().minusMonths(1);
        List<Long> viewedIds = userBehaviorRepository.findRecentViewedContentIds(userId, oneMonthAgo);
        return new HashSet<>(viewedIds);
    }
    
    /**
     * 分析用户分类偏好
     */
    private Map<Integer, Double> analyzeCategoryPreferences(Long userId) {
        LocalDateTime historyStart = LocalDateTime.now().minusDays(HISTORY_DAYS);
        
        // 获取用户分类偏好统计
        List<Object[]> categoryStats = userBehaviorRepository
            .findUserCategoryPreferences(userId, historyStart);
        
        if (categoryStats.isEmpty()) {
            return Collections.emptyMap();
        }
        
        // 计算总行为数用于归一化
        long totalBehaviors = categoryStats.stream()
            .mapToLong(data -> (Long) data[1])
            .sum();
        
        // 构建分类偏好权重映射
        Map<Integer, Double> preferences = new HashMap<>();
        for (Object[] data : categoryStats) {
            Integer categoryId = (Integer) data[0];
            Long behaviorCount = (Long) data[1];
            
            if (categoryId != null) {
                double preference = (double) behaviorCount / totalBehaviors;
                preferences.put(categoryId, preference);
            }
        }
        
        return preferences;
    }
    
    /**
     * 基于用户偏好召回内容
     */
    private List<Content> recallContentsByPreferences(Map<Integer, Double> categoryPreferences, 
                                                     Set<Long> viewedContentIds, 
                                                     RecommendRequest request) {
        
        Map<Long, Double> contentScores = new HashMap<>();
        
        // 按偏好权重排序分类
        List<Map.Entry<Integer, Double>> sortedPreferences = categoryPreferences.entrySet()
            .stream()
            .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
            .collect(Collectors.toList());
        
        for (Map.Entry<Integer, Double> entry : sortedPreferences) {
            Integer categoryId = entry.getKey();
            Double preference = entry.getValue();
            
            // 从该分类中获取内容
            List<Content> categoryContents = getCategoryContents(categoryId, request);
            
            for (Content content : categoryContents) {
                Long contentId = content.getId();
                
                // 跳过已浏览的内容
                if (viewedContentIds.contains(contentId)) {
                    continue;
                }
                
                // 计算内容分数 = 分类偏好 * 内容热度
                double contentScore = preference * content.getHotScore().doubleValue();
                contentScores.put(contentId, contentScore);
            }
        }
        
        // 按分数排序并获取Top内容
        List<Long> topContentIds = contentScores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .limit(request.getSize() * 2) // 获取更多候选，后续过滤
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        if (topContentIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 查询内容详情并过滤
        List<Content> contents = contentRepository.findAllById(topContentIds);
        
        return contents.stream()
            .filter(content -> content.isPublished())
            .filter(content -> matchesContentTypeFilter(content, request.getContentType()))
            .limit(request.getSize())
            .collect(Collectors.toList());
    }
    
    /**
     * 获取指定分类的内容
     */
    private List<Content> getCategoryContents(Integer categoryId, RecommendRequest request) {
        org.springframework.data.domain.Pageable pageable = 
            org.springframework.data.domain.PageRequest.of(0, MAX_CATEGORY_CONTENTS);
        
        List<Content> contents = contentRepository
            .findByCategoryIdAndStatus(categoryId, Content.ContentStatus.PUBLISHED, pageable)
            .getContent();
        
        return contents.stream()
            .filter(content -> matchesContentTypeFilter(content, request.getContentType()))
            .collect(Collectors.toList());
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
    
    @Override
    public String getAlgorithmName() {
        return "user_history";
    }
    
    @Override
    public double getWeight() {
        return WEIGHT;
    }
}