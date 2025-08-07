package com.recommendation.service.recall;

import com.recommendation.common.dto.RecommendRequest;
import com.recommendation.common.entity.Content;
import com.recommendation.service.recall.dto.RecallResult;
import com.recommendation.service.recall.impl.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 多路召回服务
 * 协调多个召回算法并合并结果
 * 支持需求5.1, 5.2: 推荐算法引擎 - 多路召回算法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiPathRecallService {
    
    private final CollaborativeFilteringRecallService collaborativeFilteringRecall;
    private final ContentSimilarityRecallService contentSimilarityRecall;
    private final HotContentRecallService hotContentRecall;
    private final UserHistoryRecallService userHistoryRecall;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    
    /**
     * 执行多路召回
     * 
     * @param request 推荐请求
     * @return 召回结果列表
     */
    public List<RecallResult> executeMultiPathRecall(RecommendRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 并行执行多个召回算法
            List<CompletableFuture<RecallResult>> futures = Arrays.asList(
                executeRecallAsync(collaborativeFilteringRecall, request),
                executeRecallAsync(contentSimilarityRecall, request),
                executeRecallAsync(hotContentRecall, request),
                executeRecallAsync(userHistoryRecall, request)
            );
            
            // 等待所有召回完成
            List<RecallResult> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            log.info("Multi-path recall completed for user: {}, algorithms: {}, total duration: {}ms", 
                request.getUserId(), results.size(), System.currentTimeMillis() - startTime);
            
            return results;
            
        } catch (Exception e) {
            log.error("Error in multi-path recall for user: {}", request.getUserId(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 合并多路召回结果
     * 
     * @param recallResults 召回结果列表
     * @param targetSize 目标数量
     * @return 合并后的内容列表
     */
    public List<Content> mergeRecallResults(List<RecallResult> recallResults, int targetSize) {
        if (recallResults.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 使用加权分数合并内容
        Map<Long, ContentWithScore> contentScoreMap = new HashMap<>();
        
        for (RecallResult result : recallResults) {
            double algorithmWeight = result.getWeight();
            List<Content> contents = result.getContents();
            
            for (int i = 0; i < contents.size(); i++) {
                Content content = contents.get(i);
                Long contentId = content.getId();
                
                // 计算位置权重 (排名越靠前权重越高)
                double positionWeight = 1.0 / (i + 1);
                double totalScore = algorithmWeight * positionWeight;
                
                ContentWithScore existing = contentScoreMap.get(contentId);
                if (existing != null) {
                    // 如果内容已存在，累加分数
                    existing.addScore(totalScore);
                    existing.addAlgorithm(result.getAlgorithm());
                } else {
                    // 新内容
                    ContentWithScore contentWithScore = new ContentWithScore(content, totalScore);
                    contentWithScore.addAlgorithm(result.getAlgorithm());
                    contentScoreMap.put(contentId, contentWithScore);
                }
            }
        }
        
        // 按分数排序并返回Top内容
        return contentScoreMap.values().stream()
            .sorted((c1, c2) -> Double.compare(c2.getScore(), c1.getScore()))
            .limit(targetSize)
            .map(ContentWithScore::getContent)
            .collect(Collectors.toList());
    }
    
    /**
     * 异步执行单个召回算法
     */
    private CompletableFuture<RecallResult> executeRecallAsync(RecallService recallService, 
                                                              RecommendRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.currentTimeMillis();
                
                List<Content> contents = recallService.multiPathRecall(request);
                long duration = System.currentTimeMillis() - startTime;
                
                return RecallResult.builder()
                    .contents(contents)
                    .algorithm(recallService.getAlgorithmName())
                    .weight(recallService.getWeight())
                    .count(contents.size())
                    .duration(duration)
                    .build();
                    
            } catch (Exception e) {
                log.error("Error in {} recall for user: {}", 
                    recallService.getAlgorithmName(), request.getUserId(), e);
                return null;
            }
        }, executorService);
    }
    
    /**
     * 内容与分数的包装类
     */
    private static class ContentWithScore {
        private final Content content;
        private double score;
        private final Set<String> algorithms;
        
        public ContentWithScore(Content content, double score) {
            this.content = content;
            this.score = score;
            this.algorithms = new HashSet<>();
        }
        
        public void addScore(double additionalScore) {
            this.score += additionalScore;
        }
        
        public void addAlgorithm(String algorithm) {
            this.algorithms.add(algorithm);
        }
        
        public Content getContent() {
            return content;
        }
        
        public double getScore() {
            return score;
        }
        
        public Set<String> getAlgorithms() {
            return algorithms;
        }
    }
}