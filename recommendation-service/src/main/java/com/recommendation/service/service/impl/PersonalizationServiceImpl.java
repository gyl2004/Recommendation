package com.recommendation.service.service.impl;

import com.recommendation.service.dto.RecommendResponse;
import com.recommendation.service.service.PersonalizationService;
import com.recommendation.service.service.ABTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 个性化处理服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalizationServiceImpl implements PersonalizationService {
    
    private final ABTestService abTestService;
    
    @Override
    public List<RecommendResponse.RecommendItem> adjustByUserContext(String userId, 
                                                                    List<RecommendResponse.RecommendItem> items,
                                                                    UserContext context) {
        try {
            log.debug("开始基于用户上下文调整推荐结果 - userId: {}", userId);
            
            List<RecommendResponse.RecommendItem> adjustedItems = new ArrayList<>(items);
            
            // 1. 基于设备类型调整
            adjustedItems = adjustByDeviceType(adjustedItems, context.getDeviceType());
            
            // 2. 基于时间段调整
            adjustedItems = adjustByTimeOfDay(adjustedItems, context.getTimeOfDay());
            
            // 3. 基于地理位置调整
            adjustedItems = adjustByLocation(adjustedItems, context.getLocation());
            
            // 4. 基于网络类型调整
            adjustedItems = adjustByNetworkType(adjustedItems, context.getNetworkType());
            
            // 5. 重新排序
            adjustedItems.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
            
            log.debug("用户上下文调整完成 - userId: {}, 调整后数量: {}", userId, adjustedItems.size());
            
            return adjustedItems;
            
        } catch (Exception e) {
            log.error("基于用户上下文调整推荐结果失败 - userId: {}, error: {}", userId, e.getMessage(), e);
            return items; // 返回原始结果
        }
    }
    
    @Override
    public String generateExplanation(String userId, RecommendResponse.RecommendItem item, Map<String, Object> context) {
        try {
            StringBuilder explanation = new StringBuilder();
            
            // 基于内容类型生成解释
            switch (item.getContentType()) {
                case "article":
                    explanation.append("推荐这篇文章是因为：");
                    break;
                case "video":
                    explanation.append("推荐这个视频是因为：");
                    break;
                case "product":
                    explanation.append("推荐这个商品是因为：");
                    break;
                default:
                    explanation.append("推荐这个内容是因为：");
            }
            
            // 基于标签生成解释
            if (item.getTags() != null && !item.getTags().isEmpty()) {
                explanation.append("它包含您感兴趣的标签：")
                          .append(String.join("、", item.getTags().subList(0, Math.min(3, item.getTags().size()))))
                          .append("；");
            }
            
            // 基于分数生成解释
            if (item.getScore() != null) {
                if (item.getScore() > 80) {
                    explanation.append("该内容质量很高；");
                } else if (item.getScore() > 60) {
                    explanation.append("该内容符合您的偏好；");
                }
            }
            
            // 基于时间生成解释
            if (item.getPublishTime() != null) {
                long hoursSincePublish = (System.currentTimeMillis() - item.getPublishTime()) / (1000 * 60 * 60);
                if (hoursSincePublish < 24) {
                    explanation.append("这是最新发布的内容；");
                } else if (hoursSincePublish < 168) { // 一周内
                    explanation.append("这是近期热门内容；");
                }
            }
            
            // 添加通用解释
            explanation.append("相信您会喜欢的。");
            
            return explanation.toString();
            
        } catch (Exception e) {
            log.error("生成推荐解释失败 - userId: {}, contentId: {}, error: {}", 
                    userId, item.getContentId(), e.getMessage(), e);
            return "基于您的兴趣偏好为您推荐。";
        }
    }
    
    @Override
    public Double calculateConfidence(String userId, RecommendResponse.RecommendItem item, Map<String, Double> algorithmScores) {
        try {
            if (algorithmScores == null || algorithmScores.isEmpty()) {
                return 0.5; // 默认置信度
            }
            
            // 计算算法分数的方差，方差越小置信度越高
            double mean = algorithmScores.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double variance = algorithmScores.values().stream()
                    .mapToDouble(score -> Math.pow(score - mean, 2))
                    .average().orElse(0.0);
            
            // 基于方差计算置信度（方差越小，置信度越高）
            double confidence = Math.max(0.1, Math.min(0.9, 1.0 - variance / 100.0));
            
            // 基于分数高低调整置信度
            if (item.getScore() != null) {
                if (item.getScore() > 80) {
                    confidence = Math.min(0.9, confidence + 0.1);
                } else if (item.getScore() < 40) {
                    confidence = Math.max(0.1, confidence - 0.1);
                }
            }
            
            // 基于标签匹配度调整置信度
            if (item.getTags() != null && item.getTags().size() > 2) {
                confidence = Math.min(0.9, confidence + 0.05);
            }
            
            return Math.round(confidence * 100.0) / 100.0; // 保留两位小数
            
        } catch (Exception e) {
            log.error("计算推荐置信度失败 - userId: {}, contentId: {}, error: {}", 
                    userId, item.getContentId(), e.getMessage(), e);
            return 0.5;
        }
    }
    
    @Override
    public List<RecommendResponse.RecommendItem> adjustByABTest(String userId, 
                                                               List<RecommendResponse.RecommendItem> items,
                                                               String experimentName) {
        try {
            String experimentGroup = abTestService.getExperimentGroup(userId, experimentName);
            
            log.debug("用户A/B测试分组 - userId: {}, experiment: {}, group: {}", 
                    userId, experimentName, experimentGroup);
            
            List<RecommendResponse.RecommendItem> adjustedItems = new ArrayList<>(items);
            
            // 根据实验组调整推荐策略
            switch (experimentGroup) {
                case "control":
                    // 对照组：不做调整
                    break;
                    
                case "treatment":
                    // 实验组：应用新的推荐策略
                    adjustedItems = applyTreatmentStrategy(adjustedItems, experimentName);
                    break;
                    
                default:
                    log.warn("未知的实验组 - group: {}", experimentGroup);
            }
            
            // 记录实验指标
            abTestService.recordExperimentMetric(userId, experimentName, experimentGroup, 
                    "recommendation_count", (double) adjustedItems.size());
            
            return adjustedItems;
            
        } catch (Exception e) {
            log.error("A/B测试调整失败 - userId: {}, experiment: {}, error: {}", 
                    userId, experimentName, e.getMessage(), e);
            return items; // 返回原始结果
        }
    }
    
    /**
     * 基于设备类型调整推荐结果
     */
    private List<RecommendResponse.RecommendItem> adjustByDeviceType(List<RecommendResponse.RecommendItem> items, 
                                                                    String deviceType) {
        if (deviceType == null) {
            return items;
        }
        
        return items.stream().map(item -> {
            RecommendResponse.RecommendItem adjustedItem = cloneItem(item);
            
            // 移动设备优先推荐短视频和轻量级内容
            if ("mobile".equals(deviceType)) {
                if ("video".equals(item.getContentType())) {
                    adjustedItem.setScore(item.getScore() * 1.1); // 提升视频权重
                } else if ("article".equals(item.getContentType())) {
                    adjustedItem.setScore(item.getScore() * 0.9); // 降低文章权重
                }
            }
            // PC端优先推荐长文章和详细内容
            else if ("pc".equals(deviceType)) {
                if ("article".equals(item.getContentType())) {
                    adjustedItem.setScore(item.getScore() * 1.1); // 提升文章权重
                }
            }
            
            return adjustedItem;
        }).collect(Collectors.toList());
    }
    
    /**
     * 基于时间段调整推荐结果
     */
    private List<RecommendResponse.RecommendItem> adjustByTimeOfDay(List<RecommendResponse.RecommendItem> items, 
                                                                   String timeOfDay) {
        if (timeOfDay == null) {
            timeOfDay = getCurrentTimeOfDay();
        }
        
        final String currentTimeOfDay = timeOfDay;
        
        return items.stream().map(item -> {
            RecommendResponse.RecommendItem adjustedItem = cloneItem(item);
            
            // 早晨推荐新闻和资讯类内容
            if ("morning".equals(currentTimeOfDay)) {
                if (item.getTags() != null && (item.getTags().contains("新闻") || item.getTags().contains("资讯"))) {
                    adjustedItem.setScore(item.getScore() * 1.2);
                }
            }
            // 晚上推荐娱乐和休闲类内容
            else if ("evening".equals(currentTimeOfDay)) {
                if (item.getTags() != null && (item.getTags().contains("娱乐") || item.getTags().contains("休闲"))) {
                    adjustedItem.setScore(item.getScore() * 1.2);
                }
            }
            
            return adjustedItem;
        }).collect(Collectors.toList());
    }
    
    /**
     * 基于地理位置调整推荐结果
     */
    private List<RecommendResponse.RecommendItem> adjustByLocation(List<RecommendResponse.RecommendItem> items, 
                                                                  String location) {
        if (location == null) {
            return items;
        }
        
        return items.stream().map(item -> {
            RecommendResponse.RecommendItem adjustedItem = cloneItem(item);
            
            // 基于地理位置提升本地内容权重
            if (item.getTags() != null && item.getTags().contains(location)) {
                adjustedItem.setScore(item.getScore() * 1.15);
                adjustedItem.setReason("本地推荐：" + item.getReason());
            }
            
            return adjustedItem;
        }).collect(Collectors.toList());
    }
    
    /**
     * 基于网络类型调整推荐结果
     */
    private List<RecommendResponse.RecommendItem> adjustByNetworkType(List<RecommendResponse.RecommendItem> items, 
                                                                     String networkType) {
        if (networkType == null) {
            return items;
        }
        
        return items.stream().map(item -> {
            RecommendResponse.RecommendItem adjustedItem = cloneItem(item);
            
            // 弱网环境下降低视频内容权重
            if ("2g".equals(networkType) || "3g".equals(networkType)) {
                if ("video".equals(item.getContentType())) {
                    adjustedItem.setScore(item.getScore() * 0.7);
                }
            }
            
            return adjustedItem;
        }).collect(Collectors.toList());
    }
    
    /**
     * 应用实验组策略
     */
    private List<RecommendResponse.RecommendItem> applyTreatmentStrategy(List<RecommendResponse.RecommendItem> items, 
                                                                        String experimentName) {
        // 根据不同的实验应用不同的策略
        switch (experimentName) {
            case "diversity_experiment":
                // 多样性实验：提升不同类型内容的权重
                return enhanceDiversity(items);
                
            case "freshness_experiment":
                // 新鲜度实验：提升新内容的权重
                return enhanceFreshness(items);
                
            case "personalization_experiment":
                // 个性化实验：提升个性化程度
                return enhancePersonalization(items);
                
            default:
                return items;
        }
    }
    
    /**
     * 增强多样性
     */
    private List<RecommendResponse.RecommendItem> enhanceDiversity(List<RecommendResponse.RecommendItem> items) {
        Map<String, Long> typeCount = items.stream()
                .collect(Collectors.groupingBy(RecommendResponse.RecommendItem::getContentType, Collectors.counting()));
        
        return items.stream().map(item -> {
            RecommendResponse.RecommendItem adjustedItem = cloneItem(item);
            
            // 对数量较少的内容类型提升权重
            long count = typeCount.get(item.getContentType());
            if (count < items.size() / 3) {
                adjustedItem.setScore(item.getScore() * 1.1);
            }
            
            return adjustedItem;
        }).collect(Collectors.toList());
    }
    
    /**
     * 增强新鲜度
     */
    private List<RecommendResponse.RecommendItem> enhanceFreshness(List<RecommendResponse.RecommendItem> items) {
        long currentTime = System.currentTimeMillis();
        
        return items.stream().map(item -> {
            RecommendResponse.RecommendItem adjustedItem = cloneItem(item);
            
            if (item.getPublishTime() != null) {
                long hoursSincePublish = (currentTime - item.getPublishTime()) / (1000 * 60 * 60);
                
                // 24小时内的内容提升权重
                if (hoursSincePublish < 24) {
                    adjustedItem.setScore(item.getScore() * 1.2);
                }
            }
            
            return adjustedItem;
        }).collect(Collectors.toList());
    }
    
    /**
     * 增强个性化
     */
    private List<RecommendResponse.RecommendItem> enhancePersonalization(List<RecommendResponse.RecommendItem> items) {
        return items.stream().map(item -> {
            RecommendResponse.RecommendItem adjustedItem = cloneItem(item);
            
            // 基于置信度提升个性化程度
            if (item.getConfidence() != null && item.getConfidence() > 0.7) {
                adjustedItem.setScore(item.getScore() * 1.15);
            }
            
            return adjustedItem;
        }).collect(Collectors.toList());
    }
    
    /**
     * 获取当前时间段
     */
    private String getCurrentTimeOfDay() {
        int hour = LocalDateTime.now().getHour();
        
        if (hour >= 6 && hour < 12) {
            return "morning";
        } else if (hour >= 12 && hour < 18) {
            return "afternoon";
        } else if (hour >= 18 && hour < 22) {
            return "evening";
        } else {
            return "night";
        }
    }
    
    /**
     * 克隆推荐项
     */
    private RecommendResponse.RecommendItem cloneItem(RecommendResponse.RecommendItem original) {
        return RecommendResponse.RecommendItem.builder()
                .contentId(original.getContentId())
                .contentType(original.getContentType())
                .title(original.getTitle())
                .description(original.getDescription())
                .coverUrl(original.getCoverUrl())
                .author(original.getAuthor())
                .publishTime(original.getPublishTime())
                .score(original.getScore())
                .reason(original.getReason())
                .confidence(original.getConfidence())
                .tags(original.getTags() != null ? new ArrayList<>(original.getTags()) : null)
                .extraData(original.getExtraData() != null ? new HashMap<>(original.getExtraData()) : null)
                .build();
    }
}