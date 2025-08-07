package com.recommendation.service.controller;

import com.recommendation.service.service.ABTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;

/**
 * A/B测试管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/abtest")
@RequiredArgsConstructor
public class ABTestController {
    
    private final ABTestService abTestService;
    
    /**
     * 获取用户实验分组
     */
    @GetMapping("/group")
    public ResponseEntity<String> getUserGroup(
            @RequestParam @NotBlank String userId,
            @RequestParam @NotBlank String experimentName) {
        
        try {
            String group = abTestService.getExperimentGroup(userId, experimentName);
            return ResponseEntity.ok(group);
            
        } catch (Exception e) {
            log.error("获取用户实验分组失败 - userId: {}, experiment: {}, error: {}", 
                    userId, experimentName, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 获取实验配置
     */
    @GetMapping("/config/{experimentName}")
    public ResponseEntity<ABTestService.ExperimentConfig> getExperimentConfig(
            @PathVariable @NotBlank String experimentName) {
        
        try {
            ABTestService.ExperimentConfig config = abTestService.getExperimentConfig(experimentName);
            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            log.error("获取实验配置失败 - experiment: {}, error: {}", experimentName, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 检查实验状态
     */
    @GetMapping("/status/{experimentName}")
    public ResponseEntity<Boolean> getExperimentStatus(
            @PathVariable @NotBlank String experimentName) {
        
        try {
            boolean enabled = abTestService.isExperimentEnabled(experimentName);
            return ResponseEntity.ok(enabled);
            
        } catch (Exception e) {
            log.error("检查实验状态失败 - experiment: {}, error: {}", experimentName, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 获取实验统计数据
     */
    @GetMapping("/stats/{experimentName}")
    public ResponseEntity<ABTestService.ExperimentStats> getExperimentStats(
            @PathVariable @NotBlank String experimentName) {
        
        try {
            ABTestService.ExperimentStats stats = abTestService.getExperimentStats(experimentName);
            return ResponseEntity.ok(stats);
            
        } catch (Exception e) {
            log.error("获取实验统计数据失败 - experiment: {}, error: {}", experimentName, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 记录实验指标
     */
    @PostMapping("/metric")
    public ResponseEntity<Void> recordMetric(
            @RequestParam @NotBlank String userId,
            @RequestParam @NotBlank String experimentName,
            @RequestParam @NotBlank String group,
            @RequestParam @NotBlank String metric,
            @RequestParam Double value) {
        
        try {
            abTestService.recordExperimentMetric(userId, experimentName, group, metric, value);
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("记录实验指标失败 - userId: {}, experiment: {}, group: {}, metric: {}, error: {}", 
                    userId, experimentName, group, metric, e.getMessage(), e);
            throw e;
        }
    }
}