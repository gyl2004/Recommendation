package com.recommendation.service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 离线效果评估服务
 * 负责推荐算法的离线评估和指标计算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineEvaluationService {

    /**
     * 用户行为数据类
     */
    public static class UserBehavior {
        private String userId;
        private String itemId;
        private String action; // view, click, purchase
        private double rating;
        private long timestamp;
        
        public UserBehavior(String userId, String itemId, String action, double rating, long timestamp) {
            this.userId = userId;
            this.itemId = itemId;
            this.action = action;
            this.rating = rating;
            this.timestamp = timestamp;
        }
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }
        
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public double getRating() { return rating; }
        public void setRating(double rating) { this.rating = rating; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    /**
     * 推荐结果类
     */
    public static class RecommendationResult {
        private String userId;
        private List<String> recommendedItems;
        private List<Double> scores;
        private String algorithm;
        
        public RecommendationResult(String userId, List<String> recommendedItems, 
                                  List<Double> scores, String algorithm) {
            this.userId = userId;
            this.recommendedItems = recommendedItems;
            this.scores = scores;
            this.algorithm = algorithm;
        }
        
        // Getters and setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public List<String> getRecommendedItems() { return recommendedItems; }
        public void setRecommendedItems(List<String> recommendedItems) { this.recommendedItems = recommendedItems; }
        
        public List<Double> getScores() { return scores; }
        public void setScores(List<Double> scores) { this.scores = scores; }
        
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    }

    /**
     * 评估结果类
     */
    public static class EvaluationResult {
        private String algorithm;
        private double precision;
        private double recall;
        private double f1Score;
        private double ndcg;
        private double auc;
        private double coverage;
        private double diversity;
        private double novelty;
        private Map<String, Double> additionalMetrics;
        
        public EvaluationResult() {
            this.additionalMetrics = new HashMap<>();
        }
        
        // Getters and setters
        public String getAlgorithm() { return algorithm; }
        public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
        
        public double getPrecision() { return precision; }
        public void setPrecision(double precision) { this.precision = precision; }
        
        public double getRecall() { return recall; }
        public void setRecall(double recall) { this.recall = recall; }
        
        public double getF1Score() { return f1Score; }
        public void setF1Score(double f1Score) { this.f1Score = f1Score; }
        
        public double getNdcg() { return ndcg; }
        public void setNdcg(double ndcg) { this.ndcg = ndcg; }
        
        public double getAuc() { return auc; }
        public void setAuc(double auc) { this.auc = auc; }
        
        public double getCoverage() { return coverage; }
        public void setCoverage(double coverage) { this.coverage = coverage; }
        
        public double getDiversity() { return diversity; }
        public void setDiversity(double diversity) { this.diversity = diversity; }
        
        public double getNovelty() { return novelty; }
        public void setNovelty(double novelty) { this.novelty = novelty; }
        
        public Map<String, Double> getAdditionalMetrics() { return additionalMetrics; }
        public void setAdditionalMetrics(Map<String, Double> additionalMetrics) { this.additionalMetrics = additionalMetrics; }
    }

    /**
     * 计算准确率 (Precision@K)
     */
    public double calculatePrecision(List<String> recommendedItems, Set<String> relevantItems, int k) {
        if (recommendedItems.isEmpty() || k <= 0) {
            return 0.0;
        }
        
        List<String> topK = recommendedItems.subList(0, Math.min(k, recommendedItems.size()));
        long relevantCount = topK.stream().mapToLong(item -> relevantItems.contains(item) ? 1 : 0).sum();
        
        return (double) relevantCount / topK.size();
    }

    /**
     * 计算召回率 (Recall@K)
     */
    public double calculateRecall(List<String> recommendedItems, Set<String> relevantItems, int k) {
        if (relevantItems.isEmpty() || k <= 0) {
            return 0.0;
        }
        
        List<String> topK = recommendedItems.subList(0, Math.min(k, recommendedItems.size()));
        long relevantCount = topK.stream().mapToLong(item -> relevantItems.contains(item) ? 1 : 0).sum();
        
        return (double) relevantCount / relevantItems.size();
    }

    /**
     * 计算F1分数
     */
    public double calculateF1Score(double precision, double recall) {
        if (precision + recall == 0) {
            return 0.0;
        }
        return 2 * precision * recall / (precision + recall);
    }

    /**
     * 计算NDCG (Normalized Discounted Cumulative Gain)
     */
    public double calculateNDCG(List<String> recommendedItems, Map<String, Double> relevanceScores, int k) {
        if (recommendedItems.isEmpty() || k <= 0) {
            return 0.0;
        }
        
        List<String> topK = recommendedItems.subList(0, Math.min(k, recommendedItems.size()));
        
        // 计算DCG
        double dcg = 0.0;
        for (int i = 0; i < topK.size(); i++) {
            String item = topK.get(i);
            double relevance = relevanceScores.getOrDefault(item, 0.0);
            dcg += relevance / Math.log(i + 2); // log base 2
        }
        
        // 计算IDCG (理想DCG)
        List<Double> sortedRelevances = relevanceScores.values().stream()
                                                      .sorted(Collections.reverseOrder())
                                                      .collect(Collectors.toList());
        
        double idcg = 0.0;
        for (int i = 0; i < Math.min(k, sortedRelevances.size()); i++) {
            idcg += sortedRelevances.get(i) / Math.log(i + 2);
        }
        
        return idcg > 0 ? dcg / idcg : 0.0;
    }

    /**
     * 计算AUC (Area Under Curve)
     */
    public double calculateAUC(List<String> recommendedItems, List<Double> scores, Set<String> relevantItems) {
        if (recommendedItems.size() != scores.size() || relevantItems.isEmpty()) {
            return 0.0;
        }
        
        // 创建(score, label)对
        List<Pair<Double, Integer>> scoreLabels = new ArrayList<>();
        for (int i = 0; i < recommendedItems.size(); i++) {
            String item = recommendedItems.get(i);
            double score = scores.get(i);
            int label = relevantItems.contains(item) ? 1 : 0;
            scoreLabels.add(new Pair<>(score, label));
        }
        
        // 按分数降序排序
        scoreLabels.sort((a, b) -> Double.compare(b.getFirst(), a.getFirst()));
        
        // 计算AUC
        int positives = relevantItems.size();
        int negatives = recommendedItems.size() - positives;
        
        if (positives == 0 || negatives == 0) {
            return 0.0;
        }
        
        double auc = 0.0;
        int truePositives = 0;
        
        for (Pair<Double, Integer> pair : scoreLabels) {
            if (pair.getSecond() == 1) {
                truePositives++;
            } else {
                auc += truePositives;
            }
        }
        
        return auc / (positives * negatives);
    }

    /**
     * 计算覆盖率
     */
    public double calculateCoverage(List<RecommendationResult> recommendations, Set<String> allItems) {
        Set<String> recommendedItems = new HashSet<>();
        
        for (RecommendationResult result : recommendations) {
            recommendedItems.addAll(result.getRecommendedItems());
        }
        
        return allItems.isEmpty() ? 0.0 : (double) recommendedItems.size() / allItems.size();
    }

    /**
     * 计算多样性 (基于类别)
     */
    public double calculateDiversity(List<String> recommendedItems, Map<String, String> itemCategories) {
        if (recommendedItems.isEmpty()) {
            return 0.0;
        }
        
        Set<String> categories = new HashSet<>();
        for (String item : recommendedItems) {
            String category = itemCategories.get(item);
            if (category != null) {
                categories.add(category);
            }
        }
        
        return (double) categories.size() / recommendedItems.size();
    }

    /**
     * 计算新颖性
     */
    public double calculateNovelty(List<String> recommendedItems, Map<String, Double> itemPopularity) {
        if (recommendedItems.isEmpty()) {
            return 0.0;
        }
        
        double totalNovelty = 0.0;
        int count = 0;
        
        for (String item : recommendedItems) {
            Double popularity = itemPopularity.get(item);
            if (popularity != null && popularity > 0) {
                totalNovelty += -Math.log(popularity);
                count++;
            }
        }
        
        return count > 0 ? totalNovelty / count : 0.0;
    }

    /**
     * 执行完整的离线评估
     */
    public EvaluationResult evaluateAlgorithm(String algorithm,
                                            List<RecommendationResult> recommendations,
                                            List<UserBehavior> testData,
                                            Set<String> allItems,
                                            Map<String, String> itemCategories,
                                            Map<String, Double> itemPopularity,
                                            int k) {
        
        EvaluationResult result = new EvaluationResult();
        result.setAlgorithm(algorithm);
        
        // 构建用户相关物品映射
        Map<String, Set<String>> userRelevantItems = buildUserRelevantItems(testData);
        Map<String, Map<String, Double>> userRelevanceScores = buildUserRelevanceScores(testData);
        
        double totalPrecision = 0.0;
        double totalRecall = 0.0;
        double totalNDCG = 0.0;
        double totalAUC = 0.0;
        double totalDiversity = 0.0;
        double totalNovelty = 0.0;
        
        int validUsers = 0;
        
        for (RecommendationResult recommendation : recommendations) {
            String userId = recommendation.getUserId();
            Set<String> relevantItems = userRelevantItems.get(userId);
            Map<String, Double> relevanceScores = userRelevanceScores.get(userId);
            
            if (relevantItems != null && !relevantItems.isEmpty()) {
                // 计算准确率和召回率
                double precision = calculatePrecision(recommendation.getRecommendedItems(), relevantItems, k);
                double recall = calculateRecall(recommendation.getRecommendedItems(), relevantItems, k);
                
                // 计算NDCG
                double ndcg = calculateNDCG(recommendation.getRecommendedItems(), relevanceScores, k);
                
                // 计算AUC
                double auc = calculateAUC(recommendation.getRecommendedItems(), 
                                        recommendation.getScores(), relevantItems);
                
                // 计算多样性
                double diversity = calculateDiversity(recommendation.getRecommendedItems(), itemCategories);
                
                // 计算新颖性
                double novelty = calculateNovelty(recommendation.getRecommendedItems(), itemPopularity);
                
                totalPrecision += precision;
                totalRecall += recall;
                totalNDCG += ndcg;
                totalAUC += auc;
                totalDiversity += diversity;
                totalNovelty += novelty;
                
                validUsers++;
            }
        }
        
        if (validUsers > 0) {
            result.setPrecision(totalPrecision / validUsers);
            result.setRecall(totalRecall / validUsers);
            result.setF1Score(calculateF1Score(result.getPrecision(), result.getRecall()));
            result.setNdcg(totalNDCG / validUsers);
            result.setAuc(totalAUC / validUsers);
            result.setDiversity(totalDiversity / validUsers);
            result.setNovelty(totalNovelty / validUsers);
        }
        
        // 计算覆盖率
        result.setCoverage(calculateCoverage(recommendations, allItems));
        
        // 计算额外指标
        calculateAdditionalMetrics(result, recommendations, testData);
        
        log.info("算法 {} 离线评估完成 - Precision@{}: {:.4f}, Recall@{}: {:.4f}, NDCG@{}: {:.4f}, AUC: {:.4f}",
                algorithm, k, result.getPrecision(), k, result.getRecall(), k, result.getNdcg(), result.getAuc());
        
        return result;
    }

    /**
     * 比较多个算法的性能
     */
    public Map<String, EvaluationResult> compareAlgorithms(Map<String, List<RecommendationResult>> algorithmRecommendations,
                                                          List<UserBehavior> testData,
                                                          Set<String> allItems,
                                                          Map<String, String> itemCategories,
                                                          Map<String, Double> itemPopularity,
                                                          int k) {
        
        Map<String, EvaluationResult> results = new HashMap<>();
        
        for (Map.Entry<String, List<RecommendationResult>> entry : algorithmRecommendations.entrySet()) {
            String algorithm = entry.getKey();
            List<RecommendationResult> recommendations = entry.getValue();
            
            EvaluationResult result = evaluateAlgorithm(algorithm, recommendations, testData, 
                                                       allItems, itemCategories, itemPopularity, k);
            results.put(algorithm, result);
        }
        
        // 输出比较结果
        logComparisonResults(results, k);
        
        return results;
    }

    /**
     * 生成评估报告
     */
    public Map<String, Object> generateEvaluationReport(Map<String, EvaluationResult> results) {
        Map<String, Object> report = new HashMap<>();
        
        // 基本信息
        report.put("evaluationTime", System.currentTimeMillis());
        report.put("algorithmCount", results.size());
        
        // 算法结果
        Map<String, Map<String, Double>> algorithmMetrics = new HashMap<>();
        for (Map.Entry<String, EvaluationResult> entry : results.entrySet()) {
            String algorithm = entry.getKey();
            EvaluationResult result = entry.getValue();
            
            Map<String, Double> metrics = new HashMap<>();
            metrics.put("precision", result.getPrecision());
            metrics.put("recall", result.getRecall());
            metrics.put("f1Score", result.getF1Score());
            metrics.put("ndcg", result.getNdcg());
            metrics.put("auc", result.getAuc());
            metrics.put("coverage", result.getCoverage());
            metrics.put("diversity", result.getDiversity());
            metrics.put("novelty", result.getNovelty());
            
            algorithmMetrics.put(algorithm, metrics);
        }
        report.put("algorithms", algorithmMetrics);
        
        // 最佳算法
        String bestAlgorithm = findBestAlgorithm(results);
        report.put("bestAlgorithm", bestAlgorithm);
        
        // 指标排名
        report.put("rankings", calculateRankings(results));
        
        return report;
    }

    // 私有辅助方法

    /**
     * 构建用户相关物品映射
     */
    private Map<String, Set<String>> buildUserRelevantItems(List<UserBehavior> behaviors) {
        Map<String, Set<String>> userRelevantItems = new HashMap<>();
        
        for (UserBehavior behavior : behaviors) {
            if ("click".equals(behavior.getAction()) || "purchase".equals(behavior.getAction())) {
                userRelevantItems.computeIfAbsent(behavior.getUserId(), k -> new HashSet<>())
                                .add(behavior.getItemId());
            }
        }
        
        return userRelevantItems;
    }

    /**
     * 构建用户相关性分数映射
     */
    private Map<String, Map<String, Double>> buildUserRelevanceScores(List<UserBehavior> behaviors) {
        Map<String, Map<String, Double>> userRelevanceScores = new HashMap<>();
        
        for (UserBehavior behavior : behaviors) {
            double score = 0.0;
            switch (behavior.getAction()) {
                case "view":
                    score = 1.0;
                    break;
                case "click":
                    score = 2.0;
                    break;
                case "purchase":
                    score = 3.0;
                    break;
            }
            
            if (score > 0) {
                userRelevanceScores.computeIfAbsent(behavior.getUserId(), k -> new HashMap<>())
                                  .put(behavior.getItemId(), score);
            }
        }
        
        return userRelevanceScores;
    }

    /**
     * 计算额外指标
     */
    private void calculateAdditionalMetrics(EvaluationResult result, 
                                          List<RecommendationResult> recommendations,
                                          List<UserBehavior> testData) {
        
        // 计算平均推荐列表长度
        double avgListLength = recommendations.stream()
                                            .mapToInt(r -> r.getRecommendedItems().size())
                                            .average()
                                            .orElse(0.0);
        result.getAdditionalMetrics().put("avgListLength", avgListLength);
        
        // 计算推荐物品的平均分数
        double avgScore = recommendations.stream()
                                       .flatMap(r -> r.getScores().stream())
                                       .mapToDouble(Double::doubleValue)
                                       .average()
                                       .orElse(0.0);
        result.getAdditionalMetrics().put("avgScore", avgScore);
        
        // 计算用户覆盖率
        Set<String> allUsers = testData.stream().map(UserBehavior::getUserId).collect(Collectors.toSet());
        Set<String> recommendedUsers = recommendations.stream().map(RecommendationResult::getUserId).collect(Collectors.toSet());
        double userCoverage = allUsers.isEmpty() ? 0.0 : (double) recommendedUsers.size() / allUsers.size();
        result.getAdditionalMetrics().put("userCoverage", userCoverage);
    }

    /**
     * 找到最佳算法
     */
    private String findBestAlgorithm(Map<String, EvaluationResult> results) {
        return results.entrySet().stream()
                     .max((e1, e2) -> {
                         // 综合评分：F1 * 0.4 + NDCG * 0.3 + AUC * 0.2 + Coverage * 0.1
                         double score1 = e1.getValue().getF1Score() * 0.4 + 
                                        e1.getValue().getNdcg() * 0.3 + 
                                        e1.getValue().getAuc() * 0.2 + 
                                        e1.getValue().getCoverage() * 0.1;
                         double score2 = e2.getValue().getF1Score() * 0.4 + 
                                        e2.getValue().getNdcg() * 0.3 + 
                                        e2.getValue().getAuc() * 0.2 + 
                                        e2.getValue().getCoverage() * 0.1;
                         return Double.compare(score1, score2);
                     })
                     .map(Map.Entry::getKey)
                     .orElse("unknown");
    }

    /**
     * 计算指标排名
     */
    private Map<String, List<String>> calculateRankings(Map<String, EvaluationResult> results) {
        Map<String, List<String>> rankings = new HashMap<>();
        
        // Precision排名
        rankings.put("precision", results.entrySet().stream()
                                        .sorted((e1, e2) -> Double.compare(e2.getValue().getPrecision(), e1.getValue().getPrecision()))
                                        .map(Map.Entry::getKey)
                                        .collect(Collectors.toList()));
        
        // Recall排名
        rankings.put("recall", results.entrySet().stream()
                                     .sorted((e1, e2) -> Double.compare(e2.getValue().getRecall(), e1.getValue().getRecall()))
                                     .map(Map.Entry::getKey)
                                     .collect(Collectors.toList()));
        
        // NDCG排名
        rankings.put("ndcg", results.entrySet().stream()
                                   .sorted((e1, e2) -> Double.compare(e2.getValue().getNdcg(), e1.getValue().getNdcg()))
                                   .map(Map.Entry::getKey)
                                   .collect(Collectors.toList()));
        
        // AUC排名
        rankings.put("auc", results.entrySet().stream()
                                  .sorted((e1, e2) -> Double.compare(e2.getValue().getAuc(), e1.getValue().getAuc()))
                                  .map(Map.Entry::getKey)
                                  .collect(Collectors.toList()));
        
        return rankings;
    }

    /**
     * 输出比较结果
     */
    private void logComparisonResults(Map<String, EvaluationResult> results, int k) {
        log.info("算法性能比较结果 (K={})", k);
        log.info("算法名称\t\tPrecision@{}\tRecall@{}\tF1-Score\tNDCG@{}\tAUC\t\tCoverage\tDiversity\tNovelty",
                k, k, k);
        
        for (Map.Entry<String, EvaluationResult> entry : results.entrySet()) {
            EvaluationResult result = entry.getValue();
            log.info("{}\t\t{:.4f}\t\t{:.4f}\t\t{:.4f}\t\t{:.4f}\t\t{:.4f}\t\t{:.4f}\t\t{:.4f}\t\t{:.4f}",
                    entry.getKey(),
                    result.getPrecision(),
                    result.getRecall(),
                    result.getF1Score(),
                    result.getNdcg(),
                    result.getAuc(),
                    result.getCoverage(),
                    result.getDiversity(),
                    result.getNovelty());
        }
    }

    /**
     * 简单的Pair类
     */
    private static class Pair<T, U> {
        private final T first;
        private final U second;
        
        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
        
        public T getFirst() { return first; }
        public U getSecond() { return second; }
    }
}