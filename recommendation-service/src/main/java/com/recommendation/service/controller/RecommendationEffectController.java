package com.recommendation.service.controller;

import com.recommendation.service.service.RecommendationEffectMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 推荐效果监控控制器
 * 提供推荐效果相关的API接口
 */
@Slf4j
@RestController
@RequestMapping("/recommendation-effect")
@RequiredArgsConstructor
public class RecommendationEffectController {

    private final RecommendationEffectMonitoringService effectMonitoringService;

    /**
     * 记录推荐内容曝光
     */
    @PostMapping("/impression")
    public ResponseEntity<String> recordImpression(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            String contentId = (String) request.get("contentId");
            String contentType = (String) request.get("contentType");
            String algorithm = (String) request.get("algorithm");
            Integer position = (Integer) request.get("position");
            
            effectMonitoringService.recordImpression(userId, contentId, contentType, algorithm, position);
            
            return ResponseEntity.ok("曝光记录成功");
        } catch (Exception e) {
            log.error("记录曝光失败", e);
            return ResponseEntity.status(500).body("记录失败: " + e.getMessage());
        }
    }

    /**
     * 记录推荐内容点击
     */
    @PostMapping("/click")
    public ResponseEntity<String> recordClick(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            String contentId = (String) request.get("contentId");
            String contentType = (String) request.get("contentType");
            String algorithm = (String) request.get("algorithm");
            Integer position = (Integer) request.get("position");
            Long dwellTime = Long.valueOf(request.get("dwellTime").toString());
            
            effectMonitoringService.recordClick(userId, contentId, contentType, algorithm, position, dwellTime);
            
            return ResponseEntity.ok("点击记录成功");
        } catch (Exception e) {
            log.error("记录点击失败", e);
            return ResponseEntity.status(500).body("记录失败: " + e.getMessage());
        }
    }

    /**
     * 记录推荐内容转化
     */
    @PostMapping("/conversion")
    public ResponseEntity<String> recordConversion(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            String contentId = (String) request.get("contentId");
            String contentType = (String) request.get("contentType");
            String algorithm = (String) request.get("algorithm");
            String conversionType = (String) request.get("conversionType");
            Double value = Double.valueOf(request.get("value").toString());
            
            effectMonitoringService.recordConversion(userId, contentId, contentType, algorithm, conversionType, value);
            
            return ResponseEntity.ok("转化记录成功");
        } catch (Exception e) {
            log.error("记录转化失败", e);
            return ResponseEntity.status(500).body("记录失败: " + e.getMessage());
        }
    }

    /**
     * 记录用户参与度
     */
    @PostMapping("/engagement")
    public ResponseEntity<String> recordEngagement(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            String contentType = (String) request.get("contentType");
            String algorithm = (String) request.get("algorithm");
            Long sessionDuration = Long.valueOf(request.get("sessionDuration").toString());
            Integer pageViews = (Integer) request.get("pageViews");
            Integer interactions = (Integer) request.get("interactions");
            
            effectMonitoringService.recordUserEngagement(userId, contentType, algorithm, 
                                                       sessionDuration, pageViews, interactions);
            
            return ResponseEntity.ok("参与度记录成功");
        } catch (Exception e) {
            log.error("记录参与度失败", e);
            return ResponseEntity.status(500).body("记录失败: " + e.getMessage());
        }
    }

    /**
     * 记录推荐多样性指标
     */
    @PostMapping("/diversity")
    public ResponseEntity<String> recordDiversity(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            String algorithm = (String) request.get("algorithm");
            Double categoryDiversity = Double.valueOf(request.get("categoryDiversity").toString());
            Double contentDiversity = Double.valueOf(request.get("contentDiversity").toString());
            
            effectMonitoringService.recordDiversityMetrics(userId, algorithm, categoryDiversity, contentDiversity);
            
            return ResponseEntity.ok("多样性指标记录成功");
        } catch (Exception e) {
            log.error("记录多样性指标失败", e);
            return ResponseEntity.status(500).body("记录失败: " + e.getMessage());
        }
    }

    /**
     * 记录推荐新颖性指标
     */
    @PostMapping("/novelty")
    public ResponseEntity<String> recordNovelty(@RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            String algorithm = (String) request.get("algorithm");
            Double noveltyScore = Double.valueOf(request.get("noveltyScore").toString());
            Double serendipityScore = Double.valueOf(request.get("serendipityScore").toString());
            
            effectMonitoringService.recordNoveltyMetrics(userId, algorithm, noveltyScore, serendipityScore);
            
            return ResponseEntity.ok("新颖性指标记录成功");
        } catch (Exception e) {
            log.error("记录新颖性指标失败", e);
            return ResponseEntity.status(500).body("记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取实时效果指标
     */
    @GetMapping("/metrics/realtime")
    public ResponseEntity<Map<String, Object>> getRealTimeMetrics(
            @RequestParam String contentType,
            @RequestParam String algorithm) {
        try {
            Map<String, Object> metrics = new HashMap<>();
            
            // 获取实时CTR
            double ctr = effectMonitoringService.getRealTimeCTR(contentType, algorithm);
            metrics.put("ctr", ctr);
            
            // 获取实时CVR
            double cvr = effectMonitoringService.getRealTimeCVR(contentType, algorithm);
            metrics.put("cvr", cvr);
            
            // 获取实时收入
            double revenue = effectMonitoringService.getRealTimeRevenue(contentType, algorithm);
            metrics.put("revenue", revenue);
            
            // 计算ARPU (Average Revenue Per User)
            // 这里需要用户数据，暂时使用模拟计算
            double arpu = revenue / Math.max(1, Math.random() * 1000);
            metrics.put("arpu", arpu);
            
            metrics.put("contentType", contentType);
            metrics.put("algorithm", algorithm);
            metrics.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("获取实时指标失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取效果指标摘要
     */
    @GetMapping("/metrics/summary")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        try {
            Map<String, Object> summary = new HashMap<>();
            
            // 不同内容类型的指标
            String[] contentTypes = {"article", "video", "product"};
            String[] algorithms = {"collaborative_filtering", "content_based", "deep_learning"};
            
            Map<String, Map<String, Object>> contentMetrics = new HashMap<>();
            
            for (String contentType : contentTypes) {
                Map<String, Object> typeMetrics = new HashMap<>();
                
                for (String algorithm : algorithms) {
                    Map<String, Object> algorithmMetrics = new HashMap<>();
                    algorithmMetrics.put("ctr", effectMonitoringService.getRealTimeCTR(contentType, algorithm));
                    algorithmMetrics.put("cvr", effectMonitoringService.getRealTimeCVR(contentType, algorithm));
                    algorithmMetrics.put("revenue", effectMonitoringService.getRealTimeRevenue(contentType, algorithm));
                    
                    typeMetrics.put(algorithm, algorithmMetrics);
                }
                
                contentMetrics.put(contentType, typeMetrics);
            }
            
            summary.put("contentMetrics", contentMetrics);
            summary.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("获取指标摘要失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 触发异常检测
     */
    @PostMapping("/anomaly/detect")
    public ResponseEntity<String> detectAnomalies(@RequestParam String contentType,
                                                 @RequestParam String algorithm) {
        try {
            effectMonitoringService.detectAnomalies(contentType, algorithm);
            return ResponseEntity.ok("异常检测完成");
        } catch (Exception e) {
            log.error("异常检测失败", e);
            return ResponseEntity.status(500).body("检测失败: " + e.getMessage());
        }
    }

    /**
     * 批量记录推荐效果数据
     */
    @PostMapping("/batch")
    public ResponseEntity<String> recordBatchData(@RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> events = (java.util.List<Map<String, Object>>) request.get("events");
            
            int successCount = 0;
            int errorCount = 0;
            
            for (Map<String, Object> event : events) {
                try {
                    String eventType = (String) event.get("eventType");
                    
                    switch (eventType) {
                        case "impression":
                            recordImpression(event);
                            break;
                        case "click":
                            recordClick(event);
                            break;
                        case "conversion":
                            recordConversion(event);
                            break;
                        default:
                            log.warn("未知事件类型: {}", eventType);
                            errorCount++;
                            continue;
                    }
                    successCount++;
                } catch (Exception e) {
                    log.error("处理事件失败: {}", event, e);
                    errorCount++;
                }
            }
            
            return ResponseEntity.ok(String.format("批量处理完成 - 成功: %d, 失败: %d", successCount, errorCount));
        } catch (Exception e) {
            log.error("批量记录失败", e);
            return ResponseEntity.status(500).body("批量记录失败: " + e.getMessage());
        }
    }

    /**
     * 生成测试数据
     */
    @PostMapping("/test/generate")
    public ResponseEntity<String> generateTestData(@RequestParam(defaultValue = "100") int count) {
        try {
            String[] contentTypes = {"article", "video", "product"};
            String[] algorithms = {"collaborative_filtering", "content_based", "deep_learning"};
            String[] users = {"user1", "user2", "user3", "user4", "user5"};
            
            for (int i = 0; i < count; i++) {
                String userId = users[(int) (Math.random() * users.length)];
                String contentId = "content_" + i;
                String contentType = contentTypes[(int) (Math.random() * contentTypes.length)];
                String algorithm = algorithms[(int) (Math.random() * algorithms.length)];
                int position = (int) (Math.random() * 20) + 1;
                
                // 记录曝光
                effectMonitoringService.recordImpression(userId, contentId, contentType, algorithm, position);
                
                // 30%概率点击
                if (Math.random() < 0.3) {
                    long dwellTime = (long) (Math.random() * 60000); // 0-60秒
                    effectMonitoringService.recordClick(userId, contentId, contentType, algorithm, position, dwellTime);
                    
                    // 10%概率转化
                    if (Math.random() < 0.1) {
                        double value = Math.random() * 100; // 0-100元
                        effectMonitoringService.recordConversion(userId, contentId, contentType, algorithm, "purchase", value);
                    }
                }
                
                // 记录用户参与度
                if (Math.random() < 0.2) {
                    long sessionDuration = (long) (Math.random() * 300000); // 0-5分钟
                    int pageViews = (int) (Math.random() * 10) + 1;
                    int interactions = (int) (Math.random() * 5);
                    effectMonitoringService.recordUserEngagement(userId, contentType, algorithm, 
                                                               sessionDuration, pageViews, interactions);
                }
            }
            
            return ResponseEntity.ok("测试数据生成完成，共生成 " + count + " 条记录");
        } catch (Exception e) {
            log.error("生成测试数据失败", e);
            return ResponseEntity.status(500).body("生成失败: " + e.getMessage());
        }
    }
}