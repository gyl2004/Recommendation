package com.recommendation.service.controller;

import com.recommendation.service.service.ABTestManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A/B测试管理控制器
 * 提供A/B测试相关的API接口
 */
@Slf4j
@RestController
@RequestMapping("/abtest")
@RequiredArgsConstructor
public class ABTestManagementController {

    private final ABTestManagementService abTestService;

    /**
     * 创建A/B测试实验
     */
    @PostMapping("/experiments")
    public ResponseEntity<Map<String, Object>> createExperiment(@RequestBody Map<String, Object> request) {
        try {
            String experimentName = (String) request.get("experimentName");
            String description = (String) request.get("description");
            String startTimeStr = (String) request.get("startTime");
            String endTimeStr = (String) request.get("endTime");
            
            @SuppressWarnings("unchecked")
            Map<String, Integer> groupTrafficRatio = (Map<String, Integer>) request.get("groupTrafficRatio");
            
            @SuppressWarnings("unchecked")
            List<String> targetMetrics = (List<String>) request.get("targetMetrics");
            
            LocalDateTime startTime = LocalDateTime.parse(startTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            String experimentId = abTestService.createExperiment(experimentName, description, 
                                                               startTime, endTime, 
                                                               groupTrafficRatio, targetMetrics);
            
            Map<String, Object> response = new HashMap<>();
            response.put("experimentId", experimentId);
            response.put("message", "实验创建成功");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建A/B测试实验失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 启动实验
     */
    @PostMapping("/experiments/{experimentId}/start")
    public ResponseEntity<String> startExperiment(@PathVariable String experimentId) {
        try {
            abTestService.startExperiment(experimentId);
            return ResponseEntity.ok("实验启动成功");
        } catch (Exception e) {
            log.error("启动实验失败", e);
            return ResponseEntity.status(500).body("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止实验
     */
    @PostMapping("/experiments/{experimentId}/stop")
    public ResponseEntity<String> stopExperiment(@PathVariable String experimentId) {
        try {
            abTestService.stopExperiment(experimentId);
            return ResponseEntity.ok("实验停止成功");
        } catch (Exception e) {
            log.error("停止实验失败", e);
            return ResponseEntity.status(500).body("停止失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户实验分组
     */
    @GetMapping("/experiments/{experimentId}/users/{userId}/group")
    public ResponseEntity<Map<String, Object>> getUserGroup(@PathVariable String experimentId,
                                                           @PathVariable String userId) {
        try {
            String group = abTestService.getUserGroup(userId, experimentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("experimentId", experimentId);
            response.put("userId", userId);
            response.put("group", group);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取用户分组失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 记录实验指标
     */
    @PostMapping("/experiments/{experimentId}/metrics")
    public ResponseEntity<String> recordMetric(@PathVariable String experimentId,
                                             @RequestBody Map<String, Object> request) {
        try {
            String userId = (String) request.get("userId");
            String metricType = (String) request.get("metricType");
            String group = (String) request.get("group");
            Double value = request.get("value") != null ? 
                          Double.valueOf(request.get("value").toString()) : 1.0;
            
            abTestService.recordExperimentMetric(experimentId, userId, metricType, group, value);
            
            return ResponseEntity.ok("指标记录成功");
        } catch (Exception e) {
            log.error("记录实验指标失败", e);
            return ResponseEntity.status(500).body("记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取实验结果
     */
    @GetMapping("/experiments/{experimentId}/results")
    public ResponseEntity<Map<String, Object>> getExperimentResults(@PathVariable String experimentId) {
        try {
            Map<String, Object> results = abTestService.getExperimentResults(experimentId);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("获取实验结果失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 进行统计显著性检验
     */
    @PostMapping("/experiments/{experimentId}/statistical-test")
    public ResponseEntity<Map<String, Object>> performStatisticalTest(@PathVariable String experimentId,
                                                                     @RequestParam String metric) {
        try {
            Map<String, Object> testResult = abTestService.performStatisticalTest(experimentId, metric);
            return ResponseEntity.ok(testResult);
        } catch (Exception e) {
            log.error("统计显著性检验失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取所有活跃实验
     */
    @GetMapping("/experiments/active")
    public ResponseEntity<List<ABTestManagementService.ExperimentConfig>> getActiveExperiments() {
        try {
            List<ABTestManagementService.ExperimentConfig> experiments = abTestService.getActiveExperiments();
            return ResponseEntity.ok(experiments);
        } catch (Exception e) {
            log.error("获取活跃实验失败", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 获取实验配置
     */
    @GetMapping("/experiments/{experimentId}")
    public ResponseEntity<ABTestManagementService.ExperimentConfig> getExperimentConfig(@PathVariable String experimentId) {
        try {
            ABTestManagementService.ExperimentConfig config = abTestService.getExperimentConfig(experimentId);
            if (config != null) {
                return ResponseEntity.ok(config);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("获取实验配置失败", e);
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 更新实验配置
     */
    @PutMapping("/experiments/{experimentId}")
    public ResponseEntity<String> updateExperimentConfig(@PathVariable String experimentId,
                                                        @RequestBody ABTestManagementService.ExperimentConfig config) {
        try {
            abTestService.updateExperimentConfig(experimentId, config);
            return ResponseEntity.ok("实验配置更新成功");
        } catch (Exception e) {
            log.error("更新实验配置失败", e);
            return ResponseEntity.status(500).body("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除实验
     */
    @DeleteMapping("/experiments/{experimentId}")
    public ResponseEntity<String> deleteExperiment(@PathVariable String experimentId) {
        try {
            abTestService.deleteExperiment(experimentId);
            return ResponseEntity.ok("实验删除成功");
        } catch (Exception e) {
            log.error("删除实验失败", e);
            return ResponseEntity.status(500).body("删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量记录实验指标
     */
    @PostMapping("/experiments/{experimentId}/metrics/batch")
    public ResponseEntity<String> recordBatchMetrics(@PathVariable String experimentId,
                                                    @RequestBody Map<String, Object> request) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> metrics = (List<Map<String, Object>>) request.get("metrics");
            
            int successCount = 0;
            int errorCount = 0;
            
            for (Map<String, Object> metric : metrics) {
                try {
                    String userId = (String) metric.get("userId");
                    String metricType = (String) metric.get("metricType");
                    String group = (String) metric.get("group");
                    Double value = metric.get("value") != null ? 
                                  Double.valueOf(metric.get("value").toString()) : 1.0;
                    
                    abTestService.recordExperimentMetric(experimentId, userId, metricType, group, value);
                    successCount++;
                } catch (Exception e) {
                    log.error("处理指标失败: {}", metric, e);
                    errorCount++;
                }
            }
            
            return ResponseEntity.ok(String.format("批量处理完成 - 成功: %d, 失败: %d", successCount, errorCount));
        } catch (Exception e) {
            log.error("批量记录指标失败", e);
            return ResponseEntity.status(500).body("批量记录失败: " + e.getMessage());
        }
    }

    /**
     * 生成测试实验数据
     */
    @PostMapping("/experiments/{experimentId}/test/generate")
    public ResponseEntity<String> generateTestData(@PathVariable String experimentId,
                                                  @RequestParam(defaultValue = "100") int userCount) {
        try {
            ABTestManagementService.ExperimentConfig config = abTestService.getExperimentConfig(experimentId);
            if (config == null) {
                return ResponseEntity.badRequest().body("实验不存在");
            }
            
            String[] users = new String[userCount];
            for (int i = 0; i < userCount; i++) {
                users[i] = "test_user_" + i;
            }
            
            // 为每个用户生成测试数据
            for (String userId : users) {
                String group = abTestService.getUserGroup(userId, experimentId);
                
                // 生成曝光数据
                for (int i = 0; i < 10; i++) {
                    abTestService.recordExperimentMetric(experimentId, userId, "impression", group, 1.0);
                }
                
                // 30%概率点击
                if (Math.random() < 0.3) {
                    abTestService.recordExperimentMetric(experimentId, userId, "click", group, 1.0);
                    
                    // 记录停留时间
                    double dwellTime = Math.random() * 60000; // 0-60秒
                    abTestService.recordExperimentMetric(experimentId, userId, "dwell_time", group, dwellTime);
                    
                    // 10%概率转化
                    if (Math.random() < 0.1) {
                        abTestService.recordExperimentMetric(experimentId, userId, "conversion", group, 1.0);
                        
                        // 记录收入
                        double revenue = Math.random() * 100 + 10; // 10-110元
                        abTestService.recordExperimentMetric(experimentId, userId, "revenue", group, revenue);
                    }
                }
            }
            
            return ResponseEntity.ok("测试数据生成完成，共生成 " + userCount + " 个用户的数据");
        } catch (Exception e) {
            log.error("生成测试数据失败", e);
            return ResponseEntity.status(500).body("生成失败: " + e.getMessage());
        }
    }

    /**
     * 创建预设的测试实验
     */
    @PostMapping("/experiments/create-sample")
    public ResponseEntity<Map<String, Object>> createSampleExperiment() {
        try {
            // 创建一个示例实验
            Map<String, Integer> groupRatio = new HashMap<>();
            groupRatio.put("control", 50);
            groupRatio.put("treatment", 50);
            
            List<String> targetMetrics = List.of("ctr", "cvr", "arpu");
            
            LocalDateTime startTime = LocalDateTime.now();
            LocalDateTime endTime = LocalDateTime.now().plusDays(30);
            
            String experimentId = abTestService.createExperiment(
                "推荐算法优化实验",
                "测试新的推荐算法对CTR和CVR的影响",
                startTime,
                endTime,
                groupRatio,
                targetMetrics
            );
            
            // 启动实验
            abTestService.startExperiment(experimentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("experimentId", experimentId);
            response.put("message", "示例实验创建并启动成功");
            response.put("experimentName", "推荐算法优化实验");
            response.put("groups", groupRatio.keySet());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建示例实验失败", e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}