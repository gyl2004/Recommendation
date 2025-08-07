package com.recommendation.service.controller;

import com.recommendation.service.tracing.DistributedTransactionMonitor;
import com.recommendation.service.tracing.TracingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 链路追踪控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tracing")
@RequiredArgsConstructor
public class TracingController {

    private final TracingUtils tracingUtils;
    private final DistributedTransactionMonitor transactionMonitor;

    /**
     * 获取当前链路追踪信息
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, String>> getCurrentTracing() {
        Map<String, String> result = new HashMap<>();
        
        String traceId = tracingUtils.getCurrentTraceId();
        String spanId = tracingUtils.getCurrentSpanId();
        
        result.put("traceId", traceId != null ? traceId : "N/A");
        result.put("spanId", spanId != null ? spanId : "N/A");
        result.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        return ResponseEntity.ok(result);
    }

    /**
     * 测试链路追踪功能
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> testTracing(@RequestParam(defaultValue = "test-operation") String operationName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 使用链路追踪执行测试操作
            String testResult = tracingUtils.traceSupplier("test.operation", () -> {
                tracingUtils.addTag("test.operation.name", operationName);
                tracingUtils.addEvent("test.started");
                
                // 模拟一些业务逻辑
                try {
                    Thread.sleep(100); // 模拟处理时间
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                tracingUtils.addEvent("test.completed");
                return "测试操作完成: " + operationName;
            });
            
            result.put("success", true);
            result.put("message", testResult);
            result.put("traceId", tracingUtils.getCurrentTraceId());
            result.put("spanId", tracingUtils.getCurrentSpanId());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("链路追踪测试失败", e);
            
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 开始分布式事务测试
     */
    @PostMapping("/transaction/start")
    public ResponseEntity<Map<String, String>> startTransaction(
            @RequestParam String transactionName,
            @RequestParam(defaultValue = "test-user") String userId) {
        
        try {
            String transactionId = transactionMonitor.startTransaction(transactionName, userId);
            
            Map<String, String> result = new HashMap<>();
            result.put("transactionId", transactionId);
            result.put("transactionName", transactionName);
            result.put("userId", userId);
            result.put("status", "STARTED");
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("启动分布式事务失败", e);
            
            Map<String, String> result = new HashMap<>();
            result.put("error", e.getMessage());
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 记录事务步骤
     */
    @PostMapping("/transaction/{transactionId}/step")
    public ResponseEntity<Map<String, String>> recordTransactionStep(
            @PathVariable String transactionId,
            @RequestParam String stepName,
            @RequestParam(defaultValue = "SUCCESS") String stepResult) {
        
        try {
            transactionMonitor.recordTransactionStep(transactionId, stepName, stepResult);
            
            Map<String, String> result = new HashMap<>();
            result.put("transactionId", transactionId);
            result.put("stepName", stepName);
            result.put("stepResult", stepResult);
            result.put("status", "RECORDED");
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("记录事务步骤失败", e);
            
            Map<String, String> result = new HashMap<>();
            result.put("error", e.getMessage());
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 完成分布式事务
     */
    @PostMapping("/transaction/{transactionId}/complete")
    public ResponseEntity<Map<String, String>> completeTransaction(
            @PathVariable String transactionId,
            @RequestParam(defaultValue = "true") boolean success) {
        
        try {
            transactionMonitor.completeTransaction(transactionId, success);
            
            Map<String, String> result = new HashMap<>();
            result.put("transactionId", transactionId);
            result.put("success", String.valueOf(success));
            result.put("status", "COMPLETED");
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("完成分布式事务失败", e);
            
            Map<String, String> result = new HashMap<>();
            result.put("error", e.getMessage());
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 获取事务监控统计信息
     */
    @GetMapping("/transaction/stats")
    public ResponseEntity<Map<String, Object>> getTransactionStats() {
        Map<String, Object> result = new HashMap<>();
        
        result.put("activeTransactionCount", transactionMonitor.getActiveTransactionCount());
        result.put("totalTransactionCount", transactionMonitor.getTotalTransactionCount());
        result.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(result);
    }

    /**
     * 清理超时事务
     */
    @PostMapping("/transaction/cleanup")
    public ResponseEntity<Map<String, String>> cleanupTimeoutTransactions() {
        try {
            transactionMonitor.cleanupTimeoutTransactions();
            
            Map<String, String> result = new HashMap<>();
            result.put("message", "超时事务清理完成");
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("清理超时事务失败", e);
            
            Map<String, String> result = new HashMap<>();
            result.put("error", e.getMessage());
            result.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 模拟复杂的分布式事务流程
     */
    @PostMapping("/transaction/simulate")
    public ResponseEntity<Map<String, Object>> simulateDistributedTransaction(
            @RequestParam(defaultValue = "simulate-transaction") String transactionName,
            @RequestParam(defaultValue = "test-user") String userId,
            @RequestParam(defaultValue = "3") int stepCount,
            @RequestParam(defaultValue = "false") boolean simulateError) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 开始事务
            String transactionId = transactionMonitor.startTransaction(transactionName, userId);
            result.put("transactionId", transactionId);
            
            // 执行多个步骤
            for (int i = 1; i <= stepCount; i++) {
                String stepName = "step-" + i;
                String stepResult = "SUCCESS";
                
                // 在最后一步模拟错误（如果需要）
                if (simulateError && i == stepCount) {
                    stepResult = "FAILED";
                }
                
                transactionMonitor.recordTransactionStep(transactionId, stepName, stepResult);
                
                // 模拟步骤处理时间
                Thread.sleep(50);
            }
            
            // 完成事务
            boolean success = !simulateError;
            transactionMonitor.completeTransaction(transactionId, success);
            
            result.put("success", success);
            result.put("stepCount", stepCount);
            result.put("simulateError", simulateError);
            result.put("message", "分布式事务模拟完成");
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("模拟分布式事务失败", e);
            
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.status(500).body(result);
        }
    }
}