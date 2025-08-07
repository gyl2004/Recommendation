package com.recommendation.service.controller;

import com.recommendation.service.health.HealthCheckService;
import com.recommendation.service.health.HealthStatus;
import com.recommendation.service.recovery.AutoRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuator.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthCheckService healthCheckService;
    private final AutoRecoveryService autoRecoveryService;

    /**
     * 基础健康检查
     */
    @GetMapping
    public ResponseEntity<Health> health() {
        Health health = healthCheckService.health();
        
        if (health.getStatus().getCode().equals("UP")) {
            return ResponseEntity.ok(health);
        } else {
            return ResponseEntity.status(503).body(health);
        }
    }

    /**
     * 详细健康状态
     */
    @GetMapping("/detailed")
    public ResponseEntity<HealthStatus> detailedHealth() {
        try {
            HealthStatus healthStatus = healthCheckService.getDetailedHealthStatus();
            
            if (healthStatus.isOverall()) {
                return ResponseEntity.ok(healthStatus);
            } else {
                return ResponseEntity.status(503).body(healthStatus);
            }
            
        } catch (Exception e) {
            log.error("获取详细健康状态失败", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 数据库健康检查
     */
    @GetMapping("/database")
    public ResponseEntity<Map<String, Object>> databaseHealth() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            boolean healthy = healthCheckService.checkDatabaseHealth();
            long responseTime = System.currentTimeMillis() - startTime;
            
            result.put("status", healthy ? "UP" : "DOWN");
            result.put("responseTime", responseTime + "ms");
            result.put("timestamp", System.currentTimeMillis());
            
            if (healthy) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(503).body(result);
            }
            
        } catch (Exception e) {
            log.error("数据库健康检查失败", e);
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * Redis健康检查
     */
    @GetMapping("/redis")
    public ResponseEntity<Map<String, Object>> redisHealth() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            long startTime = System.currentTimeMillis();
            boolean healthy = healthCheckService.checkRedisHealth();
            long responseTime = System.currentTimeMillis() - startTime;
            
            result.put("status", healthy ? "UP" : "DOWN");
            result.put("responseTime", responseTime + "ms");
            result.put("timestamp", System.currentTimeMillis());
            
            if (healthy) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(503).body(result);
            }
            
        } catch (Exception e) {
            log.error("Redis健康检查失败", e);
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 系统资源健康检查
     */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> systemHealth() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryUsageRatio = (double) usedMemory / totalMemory;
            
            boolean healthy = healthCheckService.checkSystemHealth();
            
            result.put("status", healthy ? "UP" : "DOWN");
            result.put("totalMemory", totalMemory);
            result.put("usedMemory", usedMemory);
            result.put("freeMemory", freeMemory);
            result.put("memoryUsagePercentage", String.format("%.2f%%", memoryUsageRatio * 100));
            result.put("availableProcessors", runtime.availableProcessors());
            result.put("timestamp", System.currentTimeMillis());
            
            if (healthy) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(503).body(result);
            }
            
        } catch (Exception e) {
            log.error("系统健康检查失败", e);
            result.put("status", "DOWN");
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 自动恢复状态
     */
    @GetMapping("/recovery/status")
    public ResponseEntity<Map<String, Object>> recoveryStatus() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("serviceHealthy", autoRecoveryService.isServiceHealthy());
        result.put("consecutiveFailures", autoRecoveryService.getConsecutiveFailures());
        result.put("lastHealthCheckTime", autoRecoveryService.getLastHealthCheckTime());
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 手动触发恢复
     */
    @PostMapping("/recovery/trigger")
    public ResponseEntity<Map<String, String>> triggerRecovery() {
        try {
            autoRecoveryService.manualRecovery();
            
            Map<String, String> result = new HashMap<>();
            result.put("message", "手动恢复已触发");
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("手动触发恢复失败", e);
            
            Map<String, String> result = new HashMap<>();
            result.put("error", "手动恢复触发失败: " + e.getMessage());
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 重置失败计数器
     */
    @PostMapping("/recovery/reset")
    public ResponseEntity<Map<String, String>> resetFailureCounter() {
        try {
            autoRecoveryService.resetFailureCounter();
            
            Map<String, String> result = new HashMap<>();
            result.put("message", "失败计数器已重置");
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("重置失败计数器失败", e);
            
            Map<String, String> result = new HashMap<>();
            result.put("error", "重置失败计数器失败: " + e.getMessage());
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 启用/禁用自动恢复
     */
    @PostMapping("/recovery/toggle")
    public ResponseEntity<Map<String, String>> toggleAutoRecovery(@RequestParam boolean enabled) {
        try {
            autoRecoveryService.setAutoRecoveryEnabled(enabled);
            
            Map<String, String> result = new HashMap<>();
            result.put("message", "自动恢复已" + (enabled ? "启用" : "禁用"));
            result.put("enabled", String.valueOf(enabled));
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("切换自动恢复状态失败", e);
            
            Map<String, String> result = new HashMap<>();
            result.put("error", "切换自动恢复状态失败: " + e.getMessage());
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.status(500).body(result);
        }
    }
}