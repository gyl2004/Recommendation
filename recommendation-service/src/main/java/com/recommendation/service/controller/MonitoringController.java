package com.recommendation.service.controller;

import com.recommendation.service.service.MonitoringService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 监控控制器
 * 提供监控相关的API接口
 */
@Slf4j
@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private final MonitoringService monitoringService;
    private final MeterRegistry meterRegistry;

    /**
     * 获取系统健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // 检查各个组件的健康状态
            health.put("status", "UP");
            health.put("timestamp", System.currentTimeMillis());
            
            // JVM状态
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> jvm = new HashMap<>();
            jvm.put("totalMemory", runtime.totalMemory());
            jvm.put("freeMemory", runtime.freeMemory());
            jvm.put("maxMemory", runtime.maxMemory());
            jvm.put("usedMemory", runtime.totalMemory() - runtime.freeMemory());
            health.put("jvm", jvm);
            
            // 系统负载
            Map<String, Object> system = new HashMap<>();
            system.put("availableProcessors", runtime.availableProcessors());
            health.put("system", system);
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("获取健康状态失败", e);
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(500).body(health);
        }
    }

    /**
     * 获取系统指标摘要
     */
    @GetMapping("/metrics/summary")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        Map<String, Object> metrics = new HashMap<>();
        
        try {
            // 推荐请求指标
            Map<String, Object> recommendation = new HashMap<>();
            recommendation.put("totalRequests", getCounterValue("recommendation.request.total"));
            recommendation.put("errorRequests", getCounterValue("recommendation.request.errors"));
            recommendation.put("avgResponseTime", getTimerMean("recommendation.request.duration"));
            metrics.put("recommendation", recommendation);
            
            // 缓存指标
            Map<String, Object> cache = new HashMap<>();
            cache.put("hits", getCounterValue("recommendation.cache.hits"));
            cache.put("misses", getCounterValue("recommendation.cache.misses"));
            double hitRate = calculateCacheHitRate();
            cache.put("hitRate", hitRate);
            metrics.put("cache", cache);
            
            // 系统资源指标
            Map<String, Object> system = new HashMap<>();
            system.put("cpuUsage", getGaugeValue("recommendation.system.cpu.usage"));
            system.put("memoryUsage", getGaugeValue("recommendation.system.memory.usage"));
            system.put("heapUsage", getGaugeValue("recommendation.system.heap.usage"));
            metrics.put("system", system);
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("获取指标摘要失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 手动记录自定义指标
     */
    @PostMapping("/metrics/custom")
    public ResponseEntity<String> recordCustomMetric(@RequestBody Map<String, Object> request) {
        try {
            String metricName = (String) request.get("metricName");
            Double value = Double.valueOf(request.get("value").toString());
            String[] tags = (String[]) request.getOrDefault("tags", new String[0]);
            
            monitoringService.recordCustomMetric(metricName, value, tags);
            
            return ResponseEntity.ok("自定义指标记录成功");
        } catch (Exception e) {
            log.error("记录自定义指标失败", e);
            return ResponseEntity.status(500).body("记录失败: " + e.getMessage());
        }
    }

    /**
     * 触发系统资源收集
     */
    @PostMapping("/collect/system")
    public ResponseEntity<String> collectSystemMetrics() {
        try {
            monitoringService.recordSystemResourceUsage();
            return ResponseEntity.ok("系统资源指标收集完成");
        } catch (Exception e) {
            log.error("收集系统资源指标失败", e);
            return ResponseEntity.status(500).body("收集失败: " + e.getMessage());
        }
    }

    /**
     * 模拟推荐请求用于测试监控
     */
    @PostMapping("/test/recommendation")
    public ResponseEntity<String> testRecommendationMetrics(@RequestParam(defaultValue = "100") int count) {
        try {
            for (int i = 0; i < count; i++) {
                // 模拟推荐请求
                long startTime = System.currentTimeMillis();
                
                // 模拟处理时间
                Thread.sleep((long) (Math.random() * 100));
                
                long duration = System.currentTimeMillis() - startTime;
                
                // 记录指标
                monitoringService.recordRecommendationRequest();
                monitoringService.recordRecommendationDuration(duration, TimeUnit.MILLISECONDS);
                
                // 随机记录一些错误
                if (Math.random() < 0.05) {
                    monitoringService.recordRecommendationError("test_error");
                }
                
                // 随机记录缓存命中/未命中
                if (Math.random() < 0.8) {
                    monitoringService.recordCacheHit("recommendation");
                } else {
                    monitoringService.recordCacheMiss("recommendation");
                }
            }
            
            return ResponseEntity.ok("测试指标记录完成，共记录 " + count + " 个请求");
        } catch (Exception e) {
            log.error("测试监控指标失败", e);
            return ResponseEntity.status(500).body("测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取计数器值
     */
    private double getCounterValue(String counterName) {
        return meterRegistry.find(counterName).counter() != null ? 
               meterRegistry.find(counterName).counter().count() : 0.0;
    }

    /**
     * 获取计时器平均值
     */
    private double getTimerMean(String timerName) {
        return meterRegistry.find(timerName).timer() != null ? 
               meterRegistry.find(timerName).timer().mean(TimeUnit.MILLISECONDS) : 0.0;
    }

    /**
     * 获取仪表值
     */
    private double getGaugeValue(String gaugeName) {
        return meterRegistry.find(gaugeName).gauge() != null ? 
               meterRegistry.find(gaugeName).gauge().value() : 0.0;
    }

    /**
     * 计算缓存命中率
     */
    private double calculateCacheHitRate() {
        double hits = getCounterValue("recommendation.cache.hits");
        double misses = getCounterValue("recommendation.cache.misses");
        double total = hits + misses;
        return total > 0 ? hits / total * 100 : 0.0;
    }
}