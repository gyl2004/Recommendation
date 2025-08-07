package com.recommendation.service.service.impl;

import com.recommendation.service.circuit.RecommendationHystrixCommand;
import com.recommendation.service.circuit.RecallHystrixCommand;
import com.recommendation.service.circuit.RankingHystrixCommand;
import com.recommendation.service.dto.RecommendRequest;
import com.recommendation.service.dto.RecommendResponse;
import com.recommendation.service.service.RecommendationService;
import com.recommendation.service.service.RecallService;
import com.recommendation.service.service.RankingService;
import com.recommendation.service.service.ReRankingService;
import com.recommendation.service.service.CacheService;
import com.recommendation.service.service.FallbackService;
import com.recommendation.service.service.PersonalizationService;
import com.recommendation.service.tracing.TracingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 推荐服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {
    
    private final RecallService recallService;
    private final RankingService rankingService;
    private final ReRankingService reRankingService;
    private final CacheService cacheService;
    private final FallbackService fallbackService;
    private final PersonalizationService personalizationService;
    private final TracingUtils tracingUtils;
    
    @Value("${recommendation.algorithm.default-recall-size:500}")
    private Integer defaultRecallSize;
    
    @Value("${recommendation.algorithm.max-recommend-size:100}")
    private Integer maxRecommendSize;
    
    @Value("${recommendation.algorithm.diversity-threshold:0.3}")
    private Double diversityThreshold;
    
    @Override
    public RecommendResponse recommend(RecommendRequest request, String requestId) {
        // 使用Hystrix熔断器包装整个推荐流程
        RecommendationHystrixCommand command = new RecommendationHystrixCommand(
                () -> executeRecommendation(request, requestId),
                fallbackService,
                request.getUserId(),
                request.getContentType(),
                request.getSize()
        );
        
        return command.execute();
    }

    /**
     * 执行推荐业务逻辑（被Hystrix包装）
     */
    private RecommendResponse executeRecommendation(RecommendRequest request, String requestId) {
        return tracingUtils.traceSupplier("recommendation.execute", () -> {
            long startTime = System.currentTimeMillis();
            
            // 记录推荐相关信息到链路追踪
            tracingUtils.recordUserInfo(request.getUserId(), null);
            tracingUtils.recordRecommendationInfo(request.getContentType(), request.getScene(), "v1.0");
            tracingUtils.addTag("request.id", requestId);
            tracingUtils.addTag("request.size", String.valueOf(request.getSize()));
            
            // 1. 检查缓存
            RecommendResponse cachedResult = tracingUtils.traceSupplier("cache.check", () -> 
                    cacheService.getRecommendResult(request.getUserId(), request.getContentType(), request.getScene()));
            
            if (cachedResult != null) {
                log.info("命中推荐缓存 - userId: {}, requestId: {}", request.getUserId(), requestId);
                tracingUtils.addTag("cache.hit", "true");
                cachedResult.setRequestId(requestId);
                cachedResult.setFromCache(true);
                return cachedResult;
            }
            
            tracingUtils.addTag("cache.hit", "false");
            
            // 2. 多路召回（使用Hystrix熔断器）
            log.info("开始多路召回 - userId: {}, requestId: {}", request.getUserId(), requestId);
            
            List<Long> recallIds = tracingUtils.traceSupplier("recall.execute", () -> {
                RecallHystrixCommand recallCommand = new RecallHystrixCommand(
                        () -> {
                            List<RecommendResponse.RecommendItem> items = recallService.recall(
                                    request.getUserId(), request.getContentType(), defaultRecallSize);
                            return items.stream().map(item -> Long.parseLong(item.getContentId())).toList();
                        },
                        fallbackService,
                        request.getUserId(),
                        request.getContentType(),
                        defaultRecallSize
                );
                return recallCommand.execute();
            });
            
            tracingUtils.addTag("recall.count", String.valueOf(recallIds.size()));
            
            if (recallIds.isEmpty()) {
                log.warn("召回结果为空，使用降级策略 - userId: {}, requestId: {}", 
                        request.getUserId(), requestId);
                tracingUtils.addTag("fallback.reason", "empty_recall");
                return fallbackService.getFallbackRecommendation(request, requestId);
            }
            
            // 3. 精确排序（使用Hystrix熔断器）
            log.info("开始精确排序 - userId: {}, 召回数量: {}, requestId: {}", 
                    request.getUserId(), recallIds.size(), requestId);
            
            List<Long> rankedIds = tracingUtils.traceSupplier("ranking.execute", () -> {
                RankingHystrixCommand rankingCommand = new RankingHystrixCommand(
                        (candidates) -> {
                            // 将ID转换为RecommendItem进行排序
                            List<RecommendResponse.RecommendItem> items = convertIdsToItems(candidates, request.getContentType());
                            List<RecommendResponse.RecommendItem> rankedItems = rankingService.rank(
                                    request.getUserId(), items, request.getScene());
                            return rankedItems.stream().map(item -> Long.parseLong(item.getContentId())).toList();
                        },
                        fallbackService,
                        recallIds,
                        request.getUserId()
                );
                return rankingCommand.execute();
            });
            
            List<RecommendResponse.RecommendItem> rankedItems = convertIdsToItems(rankedIds, request.getContentType());
            
            // 4. 重排序（多样性、业务规则等）
            log.info("开始重排序 - userId: {}, requestId: {}", request.getUserId(), requestId);
            List<RecommendResponse.RecommendItem> reRankedItems = tracingUtils.traceSupplier("reranking.execute", () ->
                    reRankingService.reRank(request.getUserId(), rankedItems, diversityThreshold));
            
            // 5. 个性化处理
            log.info("开始个性化处理 - userId: {}, requestId: {}", request.getUserId(), requestId);
            PersonalizationService.UserContext context = buildUserContext(request);
            List<RecommendResponse.RecommendItem> personalizedItems = tracingUtils.traceSupplier("personalization.execute", () ->
                    personalizationService.adjustByUserContext(request.getUserId(), reRankedItems, context));
            
            // 6. A/B测试调整
            personalizedItems = tracingUtils.traceSupplier("abtest.execute", () ->
                    personalizationService.adjustByABTest(request.getUserId(), personalizedItems, "diversity_experiment"));
            
            // 7. 生成推荐解释和置信度
            tracingUtils.traceRunnable("explanation.generate", () -> {
                for (RecommendResponse.RecommendItem item : personalizedItems) {
                    String explanation = personalizationService.generateExplanation(
                            request.getUserId(), item, new HashMap<>());
                    item.setReason(explanation);
                    
                    Double confidence = personalizationService.calculateConfidence(
                            request.getUserId(), item, Map.of("default", item.getScore()));
                    item.setConfidence(confidence);
                }
            });
            
            // 8. 截取结果
            int resultSize = Math.min(request.getSize(), personalizedItems.size());
            List<RecommendResponse.RecommendItem> finalItems = personalizedItems.subList(0, resultSize);
            
            // 9. 构建响应
            RecommendResponse response = RecommendResponse.builder()
                    .items(finalItems)
                    .total(finalItems.size())
                    .requestId(requestId)
                    .algorithmVersion("v1.0")
                    .fromCache(false)
                    .extraInfo(buildExtraInfo(startTime))
                    .build();
            
            // 10. 缓存结果
            tracingUtils.traceRunnable("cache.store", () ->
                    cacheService.cacheRecommendResult(request.getUserId(), request.getContentType(), request.getScene(), response));
            
            long duration = System.currentTimeMillis() - startTime;
            tracingUtils.addTag("total.duration", duration + "ms");
            tracingUtils.addTag("result.count", String.valueOf(finalItems.size()));
            tracingUtils.recordSlowOperation("recommendation.execute", duration, 1000);
            
            log.info("推荐完成 - userId: {}, requestId: {}, 耗时: {}ms, 结果数量: {}", 
                    request.getUserId(), requestId, duration, finalItems.size());
            
            return response;
        });
    }

    /**
     * 将ID列表转换为RecommendItem列表
     */
    private List<RecommendResponse.RecommendItem> convertIdsToItems(List<Long> ids, String contentType) {
        return ids.stream().map(id -> 
                RecommendResponse.RecommendItem.builder()
                        .contentId(String.valueOf(id))
                        .contentType(contentType)
                        .title("内容标题 " + id)
                        .score(100.0)
                        .build()
        ).toList();
    }
    
    @Override
    public String explainRecommendation(String userId, String contentId, String requestId) {
        try {
            // 获取推荐解释逻辑
            StringBuilder explanation = new StringBuilder();
            
            // 基于用户画像的解释
            explanation.append("基于您的兴趣偏好：");
            // 这里可以调用用户画像服务获取用户兴趣标签
            explanation.append("科技、体育、旅游等；");
            
            // 基于内容特征的解释
            explanation.append("该内容具有相似的特征标签；");
            
            // 基于协同过滤的解释
            explanation.append("喜欢类似内容的用户也喜欢这个内容。");
            
            return explanation.toString();
            
        } catch (Exception e) {
            log.error("获取推荐解释失败 - userId: {}, contentId: {}, error: {}", 
                    userId, contentId, e.getMessage(), e);
            return "推荐解释暂时不可用";
        }
    }
    
    @Override
    public void warmupCache(String userId) {
        try {
            log.info("开始预热用户缓存 - userId: {}", userId);
            
            // 预热不同内容类型的推荐结果
            String[] contentTypes = {"article", "video", "product", "mixed"};
            
            for (String contentType : contentTypes) {
                RecommendRequest request = RecommendRequest.builder()
                        .userId(userId)
                        .size(20)
                        .contentType(contentType)
                        .scene("default")
                        .build();
                
                recommend(request, "warmup-" + System.currentTimeMillis());
            }
            
            log.info("用户缓存预热完成 - userId: {}", userId);
            
        } catch (Exception e) {
            log.error("用户缓存预热失败 - userId: {}, error: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * 构建扩展信息
     */
    private Map<String, Object> buildExtraInfo(long startTime) {
        Map<String, Object> extraInfo = new HashMap<>();
        extraInfo.put("processTime", System.currentTimeMillis() - startTime);
        extraInfo.put("timestamp", System.currentTimeMillis());
        return extraInfo;
    }
    
    /**
     * 构建用户上下文
     */
    private PersonalizationService.UserContext buildUserContext(RecommendRequest request) {
        PersonalizationService.UserContext context = new PersonalizationService.UserContext();
        context.setDeviceType(request.getDeviceType());
        context.setLocation(request.getLocation());
        context.setExtraContext(request.getExtraParams());
        
        // 设置时间相关上下文
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int hour = now.getHour();
        if (hour >= 6 && hour < 12) {
            context.setTimeOfDay("morning");
        } else if (hour >= 12 && hour < 18) {
            context.setTimeOfDay("afternoon");
        } else if (hour >= 18 && hour < 22) {
            context.setTimeOfDay("evening");
        } else {
            context.setTimeOfDay("night");
        }
        
        context.setDayOfWeek(now.getDayOfWeek().toString().toLowerCase());
        
        return context;
    }
}