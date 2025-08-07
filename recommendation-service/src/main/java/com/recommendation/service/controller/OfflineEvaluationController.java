package com.recommendation.service.controller;

import com.recommendation.service.service.OfflineEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 离线评估控制器
 * 提供推荐算法离线评估相关的API接口
 */
@Slf4j
@RestController
@RequestMapping("/offline-evaluation")
@RequiredArgsConstructor
public class OfflineEvaluationController {

    private final OfflineEvaluationService offlineEvaluationService;

    /**
     * 评估单个算法
     */
    @PostMapping("/evaluate")
    public ResponseEntity<OfflineEvaluationService.EvaluationResult> evaluateAlgorithm(@RequestBody Map<String, Object> request) {
        try {
            String algorithm = (String) request.get("algorithm");
            Integer k = (Integer) request.getOrDefault("k", 10);
            
            // 解析推荐结果
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recommendationsData = (List<Map<String, Object>>) request.get("recommendations");
            List<OfflineEvaluationService.RecommendationResult> recommendations = parseRecommendations(recommendationsData);
            
            // 解析测试数据
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> testDataRaw = (List<Map<String, Object>>) request.get("testData");
            List<OfflineEvaluationService.UserBehavior> testData = parseUserBehaviors(testDataRaw);
            
            // 解析其他参数
            @SuppressWarnings("unchecked")
            Set<String> allItems = new HashSet<>((List<String>) request.getOrDefault("allItems", new ArrayList<>()));
            
            @SuppressWarnings("unchecked")
            Map<String, String> itemCategories = (Map<String, String>) request.getOrDefault("itemCategories", new HashMap<>());
            
            @SuppressWarnings("unchecked")
            Map<String, Double> itemPopularity = (Map<String, Double>) request.getOrDefault("itemPopularity", new HashMap<>());
            
            // 执行评估
            OfflineEvaluationService.EvaluationResult result = offlineEvaluationService.evaluateAlgorithm(
                algorithm, recommendations, testData, allItems, itemCategories, itemPopularity, k);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("算法评估失败", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 比较多个算法
     */
    @PostMapping("/compare")
    public ResponseEntity<Map<String, OfflineEvaluationService.EvaluationResult>> compareAlgorithms(@RequestBody Map<String, Object> request) {
        try {
            Integer k = (Integer) request.getOrDefault("k", 10);
            
            // 解析算法推荐结果
            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, Object>>> algorithmRecommendationsRaw = 
                (Map<String, List<Map<String, Object>>>) request.get("algorithmRecommendations");
            
            Map<String, List<OfflineEvaluationService.RecommendationResult>> algorithmRecommendations = new HashMap<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : algorithmRecommendationsRaw.entrySet()) {
                algorithmRecommendations.put(entry.getKey(), parseRecommendations(entry.getValue()));
            }
            
            // 解析测试数据
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> testDataRaw = (List<Map<String, Object>>) request.get("testData");
            List<OfflineEvaluationService.UserBehavior> testData = parseUserBehaviors(testDataRaw);
            
            // 解析其他参数
            @SuppressWarnings("unchecked")
            Set<String> allItems = new HashSet<>((List<String>) request.getOrDefault("allItems", new ArrayList<>()));
            
            @SuppressWarnings("unchecked")
            Map<String, String> itemCategories = (Map<String, String>) request.getOrDefault("itemCategories", new HashMap<>());
            
            @SuppressWarnings("unchecked")
            Map<String, Double> itemPopularity = (Map<String, Double>) request.getOrDefault("itemPopularity", new HashMap<>());
            
            // 执行比较
            Map<String, OfflineEvaluationService.EvaluationResult> results = offlineEvaluationService.compareAlgorithms(
                algorithmRecommendations, testData, allItems, itemCategories, itemPopularity, k);
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("算法比较失败", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 生成评估报告
     */
    @PostMapping("/report")
    public ResponseEntity<Map<String, Object>> generateReport(@RequestBody Map<String, OfflineEvaluationService.EvaluationResult> results) {
        try {
            Map<String, Object> report = offlineEvaluationService.generateEvaluationReport(results);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("生成评估报告失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 计算单个指标
     */
    @PostMapping("/metrics/precision")
    public ResponseEntity<Map<String, Object>> calculatePrecision(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> recommendedItems = (List<String>) request.get("recommendedItems");
            
            @SuppressWarnings("unchecked")
            Set<String> relevantItems = new HashSet<>((List<String>) request.get("relevantItems"));
            
            Integer k = (Integer) request.getOrDefault("k", 10);
            
            double precision = offlineEvaluationService.calculatePrecision(recommendedItems, relevantItems, k);
            
            Map<String, Object> response = new HashMap<>();
            response.put("precision", precision);
            response.put("k", k);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("计算Precision失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 计算召回率
     */
    @PostMapping("/metrics/recall")
    public ResponseEntity<Map<String, Object>> calculateRecall(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> recommendedItems = (List<String>) request.get("recommendedItems");
            
            @SuppressWarnings("unchecked")
            Set<String> relevantItems = new HashSet<>((List<String>) request.get("relevantItems"));
            
            Integer k = (Integer) request.getOrDefault("k", 10);
            
            double recall = offlineEvaluationService.calculateRecall(recommendedItems, relevantItems, k);
            
            Map<String, Object> response = new HashMap<>();
            response.put("recall", recall);
            response.put("k", k);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("计算Recall失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 计算NDCG
     */
    @PostMapping("/metrics/ndcg")
    public ResponseEntity<Map<String, Object>> calculateNDCG(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> recommendedItems = (List<String>) request.get("recommendedItems");
            
            @SuppressWarnings("unchecked")
            Map<String, Double> relevanceScores = (Map<String, Double>) request.get("relevanceScores");
            
            Integer k = (Integer) request.getOrDefault("k", 10);
            
            double ndcg = offlineEvaluationService.calculateNDCG(recommendedItems, relevanceScores, k);
            
            Map<String, Object> response = new HashMap<>();
            response.put("ndcg", ndcg);
            response.put("k", k);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("计算NDCG失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 计算覆盖率
     */
    @PostMapping("/metrics/coverage")
    public ResponseEntity<Map<String, Object>> calculateCoverage(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> recommendationsData = (List<Map<String, Object>>) request.get("recommendations");
            List<OfflineEvaluationService.RecommendationResult> recommendations = parseRecommendations(recommendationsData);
            
            @SuppressWarnings("unchecked")
            Set<String> allItems = new HashSet<>((List<String>) request.get("allItems"));
            
            double coverage = offlineEvaluationService.calculateCoverage(recommendations, allItems);
            
            Map<String, Object> response = new HashMap<>();
            response.put("coverage", coverage);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("计算Coverage失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 计算多样性
     */
    @PostMapping("/metrics/diversity")
    public ResponseEntity<Map<String, Object>> calculateDiversity(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> recommendedItems = (List<String>) request.get("recommendedItems");
            
            @SuppressWarnings("unchecked")
            Map<String, String> itemCategories = (Map<String, String>) request.get("itemCategories");
            
            double diversity = offlineEvaluationService.calculateDiversity(recommendedItems, itemCategories);
            
            Map<String, Object> response = new HashMap<>();
            response.put("diversity", diversity);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("计算Diversity失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 计算新颖性
     */
    @PostMapping("/metrics/novelty")
    public ResponseEntity<Map<String, Object>> calculateNovelty(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<String> recommendedItems = (List<String>) request.get("recommendedItems");
            
            @SuppressWarnings("unchecked")
            Map<String, Double> itemPopularity = (Map<String, Double>) request.get("itemPopularity");
            
            double novelty = offlineEvaluationService.calculateNovelty(recommendedItems, itemPopularity);
            
            Map<String, Object> response = new HashMap<>();
            response.put("novelty", novelty);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("计算Novelty失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 生成测试数据
     */
    @PostMapping("/test/generate")
    public ResponseEntity<Map<String, Object>> generateTestData(@RequestParam(defaultValue = "100") int userCount,
                                                               @RequestParam(defaultValue = "1000") int itemCount,
                                                               @RequestParam(defaultValue = "10") int recommendationSize) {
        try {
            // 生成用户行为数据
            List<OfflineEvaluationService.UserBehavior> testData = generateUserBehaviors(userCount, itemCount);
            
            // 生成推荐结果
            Map<String, List<OfflineEvaluationService.RecommendationResult>> algorithmRecommendations = 
                generateRecommendations(userCount, itemCount, recommendationSize);
            
            // 生成物品元数据
            Set<String> allItems = generateAllItems(itemCount);
            Map<String, String> itemCategories = generateItemCategories(allItems);
            Map<String, Double> itemPopularity = generateItemPopularity(allItems);
            
            Map<String, Object> response = new HashMap<>();
            response.put("testData", testData);
            response.put("algorithmRecommendations", algorithmRecommendations);
            response.put("allItems", allItems);
            response.put("itemCategories", itemCategories);
            response.put("itemPopularity", itemPopularity);
            response.put("message", String.format("测试数据生成完成 - 用户数: %d, 物品数: %d, 推荐大小: %d", 
                                                 userCount, itemCount, recommendationSize));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("生成测试数据失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 执行完整的评估流程
     */
    @PostMapping("/full-evaluation")
    public ResponseEntity<Map<String, Object>> performFullEvaluation(@RequestParam(defaultValue = "10") int k) {
        try {
            // 生成测试数据
            int userCount = 50;
            int itemCount = 200;
            int recommendationSize = 20;
            
            List<OfflineEvaluationService.UserBehavior> testData = generateUserBehaviors(userCount, itemCount);
            Map<String, List<OfflineEvaluationService.RecommendationResult>> algorithmRecommendations = 
                generateRecommendations(userCount, itemCount, recommendationSize);
            Set<String> allItems = generateAllItems(itemCount);
            Map<String, String> itemCategories = generateItemCategories(allItems);
            Map<String, Double> itemPopularity = generateItemPopularity(allItems);
            
            // 执行算法比较
            Map<String, OfflineEvaluationService.EvaluationResult> results = 
                offlineEvaluationService.compareAlgorithms(algorithmRecommendations, testData, 
                                                          allItems, itemCategories, itemPopularity, k);
            
            // 生成报告
            Map<String, Object> report = offlineEvaluationService.generateEvaluationReport(results);
            
            Map<String, Object> response = new HashMap<>();
            response.put("evaluationResults", results);
            response.put("report", report);
            response.put("testDataSize", testData.size());
            response.put("userCount", userCount);
            response.put("itemCount", itemCount);
            response.put("k", k);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("完整评估失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    // 私有辅助方法

    /**
     * 解析推荐结果
     */
    private List<OfflineEvaluationService.RecommendationResult> parseRecommendations(List<Map<String, Object>> data) {
        return data.stream().map(item -> {
            String userId = (String) item.get("userId");
            @SuppressWarnings("unchecked")
            List<String> recommendedItems = (List<String>) item.get("recommendedItems");
            @SuppressWarnings("unchecked")
            List<Double> scores = (List<Double>) item.get("scores");
            String algorithm = (String) item.get("algorithm");
            
            return new OfflineEvaluationService.RecommendationResult(userId, recommendedItems, scores, algorithm);
        }).collect(Collectors.toList());
    }

    /**
     * 解析用户行为数据
     */
    private List<OfflineEvaluationService.UserBehavior> parseUserBehaviors(List<Map<String, Object>> data) {
        return data.stream().map(item -> {
            String userId = (String) item.get("userId");
            String itemId = (String) item.get("itemId");
            String action = (String) item.get("action");
            Double rating = Double.valueOf(item.get("rating").toString());
            Long timestamp = Long.valueOf(item.get("timestamp").toString());
            
            return new OfflineEvaluationService.UserBehavior(userId, itemId, action, rating, timestamp);
        }).collect(Collectors.toList());
    }

    /**
     * 生成用户行为数据
     */
    private List<OfflineEvaluationService.UserBehavior> generateUserBehaviors(int userCount, int itemCount) {
        List<OfflineEvaluationService.UserBehavior> behaviors = new ArrayList<>();
        Random random = new Random();
        String[] actions = {"view", "click", "purchase"};
        
        for (int u = 1; u <= userCount; u++) {
            String userId = "user_" + u;
            
            // 每个用户生成10-50个行为
            int behaviorCount = random.nextInt(41) + 10;
            
            for (int b = 0; b < behaviorCount; b++) {
                String itemId = "item_" + (random.nextInt(itemCount) + 1);
                String action = actions[random.nextInt(actions.length)];
                double rating = random.nextDouble() * 5; // 0-5分
                long timestamp = System.currentTimeMillis() - random.nextInt(86400000); // 过去24小时内
                
                behaviors.add(new OfflineEvaluationService.UserBehavior(userId, itemId, action, rating, timestamp));
            }
        }
        
        return behaviors;
    }

    /**
     * 生成推荐结果
     */
    private Map<String, List<OfflineEvaluationService.RecommendationResult>> generateRecommendations(int userCount, int itemCount, int recommendationSize) {
        Map<String, List<OfflineEvaluationService.RecommendationResult>> algorithmRecommendations = new HashMap<>();
        String[] algorithms = {"collaborative_filtering", "content_based", "deep_learning"};
        Random random = new Random();
        
        for (String algorithm : algorithms) {
            List<OfflineEvaluationService.RecommendationResult> recommendations = new ArrayList<>();
            
            for (int u = 1; u <= userCount; u++) {
                String userId = "user_" + u;
                List<String> recommendedItems = new ArrayList<>();
                List<Double> scores = new ArrayList<>();
                
                for (int i = 0; i < recommendationSize; i++) {
                    String itemId = "item_" + (random.nextInt(itemCount) + 1);
                    double score = random.nextDouble(); // 0-1分
                    
                    recommendedItems.add(itemId);
                    scores.add(score);
                }
                
                // 按分数降序排序
                List<Integer> indices = new ArrayList<>();
                for (int i = 0; i < recommendationSize; i++) {
                    indices.add(i);
                }
                indices.sort((i, j) -> Double.compare(scores.get(j), scores.get(i)));
                
                List<String> sortedItems = indices.stream().map(recommendedItems::get).collect(Collectors.toList());
                List<Double> sortedScores = indices.stream().map(scores::get).collect(Collectors.toList());
                
                recommendations.add(new OfflineEvaluationService.RecommendationResult(userId, sortedItems, sortedScores, algorithm));
            }
            
            algorithmRecommendations.put(algorithm, recommendations);
        }
        
        return algorithmRecommendations;
    }

    /**
     * 生成所有物品集合
     */
    private Set<String> generateAllItems(int itemCount) {
        Set<String> allItems = new HashSet<>();
        for (int i = 1; i <= itemCount; i++) {
            allItems.add("item_" + i);
        }
        return allItems;
    }

    /**
     * 生成物品分类
     */
    private Map<String, String> generateItemCategories(Set<String> allItems) {
        Map<String, String> itemCategories = new HashMap<>();
        String[] categories = {"电子产品", "服装", "图书", "食品", "家居", "运动", "美妆", "汽车"};
        Random random = new Random();
        
        for (String item : allItems) {
            String category = categories[random.nextInt(categories.length)];
            itemCategories.put(item, category);
        }
        
        return itemCategories;
    }

    /**
     * 生成物品流行度
     */
    private Map<String, Double> generateItemPopularity(Set<String> allItems) {
        Map<String, Double> itemPopularity = new HashMap<>();
        Random random = new Random();
        
        for (String item : allItems) {
            // 使用幂律分布模拟真实的物品流行度分布
            double popularity = Math.pow(random.nextDouble(), 2); // 0-1之间，偏向小值
            itemPopularity.put(item, popularity);
        }
        
        return itemPopularity;
    }
}