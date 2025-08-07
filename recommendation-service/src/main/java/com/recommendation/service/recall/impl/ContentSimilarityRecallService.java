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
 * 内容相似度召回服务
 * 使用余弦相似度和标签匹配进行召回
 * 支持需求5.1, 5.2: 推荐算法引擎 - 内容相似度召回算法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSimilarityRecallService implements RecallService {
    
    private final UserBehaviorRepository userBehaviorRepository;
    private final ContentRepository contentRepository;
    
    private static final int SEED_CONTENT_LIMIT = 20;
    private static final int SIMILAR_CONTENT_PER_SEED = 5;
    private static final double WEIGHT = 0.25;
    
    @Override
    public List<Content> multiPathRecall(RecommendRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            Long userId = Long.parseLong(request.getUserId());
            
            // 1. 获取用户最近喜欢的内容作为种子内容
            List<Content> seedContents = getUserPreferredContents(userId);
            if (seedContents.isEmpty()) {
                log.info("No seed contents found for user: {}", userId);
                return Collections.emptyList();
            }
            
            // 2. 获取用户已浏览的内容，避免重复推荐
            Set<Long> viewedContentIds = getViewedContentIds(userId);
            
            // 3. 基于种子内容查找相似内容
            List<Content> similarContents = findSimilarContents(
                seedContents, viewedContentIds, request);
            
            log.info("Content similarity recall completed for user: {}, recalled: {} contents, duration: {}ms", 
                userId, similarContents.size(), System.currentTimeMillis() - startTime);
            
            return similarContents;
            
        } catch (Exception e) {
            log.error("Error in content similarity recall for user: {}", request.getUserId(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 获取用户最近喜欢的内容作为种子
     */
    private List<Content> getUserPreferredContents(Long userId) {
        // 获取用户最近的正向行为
        List<UserBehavior> preferredBehaviors = userBehaviorRepository
            .findUserPreferredContents(userId);
        
        // 提取内容ID并查询内容详情
        List<Long> contentIds = preferredBehaviors.stream()
            .limit(SEED_CONTENT_LIMIT)
            .map(UserBehavior::getContentId)
            .collect(Collectors.toList());
        
        if (contentIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        return contentRepository.findAllById(contentIds).stream()
            .filter(Content::isPublished)
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
     * 基于种子内容查找相似内容
     */
    private List<Content> findSimilarContents(List<Content> seedContents, 
                                            Set<Long> viewedContentIds, 
                                            RecommendRequest request) {
        
        Map<Long, Double> contentScores = new HashMap<>();
        
        for (Content seedContent : seedContents) {
            // 基于分类和标签查找相似内容
            List<Content> similarContents = findSimilarContentsBySeed(seedContent);
            
            for (Content similarContent : similarContents) {
                Long contentId = similarContent.getId();
                
                // 跳过种子内容本身和已浏览的内容
                if (contentId.equals(seedContent.getId()) || viewedContentIds.contains(contentId)) {
                    continue;
                }
                
                // 计算相似度分数
                double similarity = calculateContentSimilarity(seedContent, similarContent);
                contentScores.merge(contentId, similarity, Double::sum);
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
     * 基于单个种子内容查找相似内容
     */
    private List<Content> findSimilarContentsBySeed(Content seedContent) {
        // 构建标签JSON字符串用于查询
        String tagsJson = buildTagsJsonString(seedContent.getTags());
        
        return contentRepository.findSimilarContents(
            seedContent.getId(),
            seedContent.getCategoryId(),
            tagsJson,
            SIMILAR_CONTENT_PER_SEED
        );
    }
    
    /**
     * 计算两个内容之间的相似度
     */
    private double calculateContentSimilarity(Content content1, Content content2) {
        double similarity = 0.0;
        
        // 1. 分类相似度 (权重: 0.4)
        if (Objects.equals(content1.getCategoryId(), content2.getCategoryId())) {
            similarity += 0.4;
        }
        
        // 2. 标签相似度 (权重: 0.4)
        double tagSimilarity = calculateTagSimilarity(content1.getTags(), content2.getTags());
        similarity += tagSimilarity * 0.4;
        
        // 3. 内容类型相似度 (权重: 0.2)
        if (content1.getContentType() == content2.getContentType()) {
            similarity += 0.2;
        }
        
        return similarity;
    }
    
    /**
     * 计算标签相似度 (Jaccard相似度)
     */
    private double calculateTagSimilarity(List<String> tags1, List<String> tags2) {
        if (tags1 == null || tags2 == null || tags1.isEmpty() || tags2.isEmpty()) {
            return 0.0;
        }
        
        Set<String> set1 = new HashSet<>(tags1);
        Set<String> set2 = new HashSet<>(tags2);
        
        // 计算交集
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        
        // 计算并集
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        
        // Jaccard相似度 = |交集| / |并集|
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
    
    /**
     * 构建标签JSON字符串用于数据库查询
     */
    private String buildTagsJsonString(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        
        return "[\"" + String.join("\",\"", tags) + "\"]";
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
        return "content_similarity";
    }
    
    @Override
    public double getWeight() {
        return WEIGHT;
    }
}