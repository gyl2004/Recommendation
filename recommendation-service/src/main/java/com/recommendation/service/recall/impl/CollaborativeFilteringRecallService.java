package com.recommendation.service.recall.impl;

import com.recommendation.common.dto.RecommendRequest;
import com.recommendation.common.entity.Content;
import com.recommendation.common.entity.UserBehavior;
import com.recommendation.common.repository.ContentRepository;
import com.recommendation.common.repository.UserBehaviorRepository;
import com.recommendation.service.recall.RecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 协同过滤召回服务
 * 基于用户-物品交互矩阵进行召回
 * 支持需求5.1, 5.2: 推荐算法引擎 - 协同过滤召回算法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborativeFilteringRecallService implements RecallService {
    
    private final UserBehaviorRepository userBehaviorRepository;
    private final ContentRepository contentRepository;
    
    private static final int SIMILAR_USER_LIMIT = 50;
    private static final int MIN_COMMON_CONTENTS = 3;
    private static final int RECALL_LIMIT = 100;
    private static final double WEIGHT = 0.3;
    
    @Override
    public List<Content> multiPathRecall(RecommendRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            Long userId = Long.parseLong(request.getUserId());
            
            // 1. 查找相似用户
            List<Long> similarUsers = findSimilarUsers(userId);
            if (similarUsers.isEmpty()) {
                log.info("No similar users found for user: {}", userId);
                return Collections.emptyList();
            }
            
            // 2. 获取用户已浏览的内容，避免重复推荐
            Set<Long> viewedContentIds = getViewedContentIds(userId);
            
            // 3. 基于相似用户的行为进行内容召回
            List<Content> recalledContents = recallContentsBySimilarUsers(
                similarUsers, viewedContentIds, request);
            
            log.info("Collaborative filtering recall completed for user: {}, recalled: {} contents, duration: {}ms", 
                userId, recalledContents.size(), System.currentTimeMillis() - startTime);
            
            return recalledContents;
            
        } catch (Exception e) {
            log.error("Error in collaborative filtering recall for user: {}", request.getUserId(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 查找相似用户
     * 基于共同喜欢的内容计算用户相似度
     */
    private List<Long> findSimilarUsers(Long userId) {
        // 查找与当前用户有共同行为的其他用户
        List<Object[]> similarUserData = userBehaviorRepository.findSimilarUsers(
            userId, (long) MIN_COMMON_CONTENTS);
        
        return similarUserData.stream()
            .limit(SIMILAR_USER_LIMIT)
            .map(data -> (Long) data[0])
            .collect(Collectors.toList());
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
     * 基于相似用户的行为召回内容
     */
    private List<Content> recallContentsBySimilarUsers(List<Long> similarUsers, 
                                                      Set<Long> viewedContentIds, 
                                                      RecommendRequest request) {
        
        // 统计相似用户喜欢的内容及其权重
        Map<Long, Double> contentScores = new HashMap<>();
        
        for (Long similarUserId : similarUsers) {
            // 获取相似用户的正向行为内容
            List<UserBehavior> preferredBehaviors = userBehaviorRepository
                .findUserPreferredContents(similarUserId);
            
            for (UserBehavior behavior : preferredBehaviors) {
                Long contentId = behavior.getContentId();
                
                // 跳过用户已浏览的内容
                if (viewedContentIds.contains(contentId)) {
                    continue;
                }
                
                // 计算内容权重分数
                double score = behavior.getActionWeight();
                contentScores.merge(contentId, score, Double::sum);
            }
        }
        
        // 按分数排序并获取Top内容
        List<Long> topContentIds = contentScores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .limit(RECALL_LIMIT)
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
        return "collaborative_filtering";
    }
    
    @Override
    public double getWeight() {
        return WEIGHT;
    }
}