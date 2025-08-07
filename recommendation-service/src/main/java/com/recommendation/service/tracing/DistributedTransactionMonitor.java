package com.recommendation.service.tracing;

import brave.Span;
import brave.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 分布式事务监控组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedTransactionMonitor {

    private final Tracer tracer;
    private final TracingUtils tracingUtils;

    // 事务计数器
    private final AtomicLong transactionCounter = new AtomicLong(0);
    
    // 活跃事务追踪
    private final Map<String, TransactionInfo> activeTransactions = new ConcurrentHashMap<>();

    /**
     * 开始分布式事务
     *
     * @param transactionName 事务名称
     * @param userId 用户ID
     * @return 事务ID
     */
    public String startTransaction(String transactionName, String userId) {
        String transactionId = generateTransactionId();
        
        Span transactionSpan = tracer.nextSpan()
                .name("distributed-transaction")
                .tag("transaction.id", transactionId)
                .tag("transaction.name", transactionName)
                .tag("transaction.type", "distributed")
                .tag("user.id", userId)
                .start();

        TransactionInfo transactionInfo = new TransactionInfo(
                transactionId, transactionName, userId, 
                System.currentTimeMillis(), transactionSpan);
        
        activeTransactions.put(transactionId, transactionInfo);
        
        log.info("开始分布式事务: {}, 事务ID: {}, 用户: {}, TraceId: {}", 
                transactionName, transactionId, userId, 
                transactionSpan.context().traceId());
        
        return transactionId;
    }

    /**
     * 记录事务步骤
     *
     * @param transactionId 事务ID
     * @param stepName 步骤名称
     * @param stepResult 步骤结果
     */
    public void recordTransactionStep(String transactionId, String stepName, String stepResult) {
        TransactionInfo transactionInfo = activeTransactions.get(transactionId);
        if (transactionInfo == null) {
            log.warn("未找到事务信息: {}", transactionId);
            return;
        }

        Span stepSpan = tracer.nextSpan()
                .name("transaction-step")
                .tag("transaction.id", transactionId)
                .tag("step.name", stepName)
                .tag("step.result", stepResult)
                .tag("step.order", String.valueOf(transactionInfo.getStepCount()))
                .start();

        try (Tracer.SpanInScope ws = tracer.withSpanInScope(stepSpan)) {
            transactionInfo.incrementStepCount();
            
            log.debug("记录事务步骤: 事务ID: {}, 步骤: {}, 结果: {}, TraceId: {}", 
                    transactionId, stepName, stepResult, stepSpan.context().traceId());
            
            // 如果步骤失败，标记为错误
            if ("FAILED".equalsIgnoreCase(stepResult) || "ERROR".equalsIgnoreCase(stepResult)) {
                stepSpan.tag("error", "true");
                transactionInfo.setHasError(true);
            }
            
        } finally {
            stepSpan.end();
        }
    }

    /**
     * 完成分布式事务
     *
     * @param transactionId 事务ID
     * @param success 是否成功
     */
    public void completeTransaction(String transactionId, boolean success) {
        TransactionInfo transactionInfo = activeTransactions.remove(transactionId);
        if (transactionInfo == null) {
            log.warn("未找到事务信息: {}", transactionId);
            return;
        }

        Span transactionSpan = transactionInfo.getTransactionSpan();
        long duration = System.currentTimeMillis() - transactionInfo.getStartTime();
        
        transactionSpan.tag("transaction.result", success ? "SUCCESS" : "FAILED");
        transactionSpan.tag("transaction.duration", duration + "ms");
        transactionSpan.tag("transaction.steps", String.valueOf(transactionInfo.getStepCount()));
        
        if (!success || transactionInfo.isHasError()) {
            transactionSpan.tag("error", "true");
        }
        
        // 记录慢事务
        if (duration > 5000) { // 超过5秒的事务
            transactionSpan.tag("slow.transaction", "true");
            log.warn("检测到慢事务: {}, 耗时: {}ms, 事务ID: {}", 
                    transactionInfo.getTransactionName(), duration, transactionId);
        }
        
        transactionSpan.end();
        
        log.info("完成分布式事务: {}, 事务ID: {}, 结果: {}, 耗时: {}ms, 步骤数: {}", 
                transactionInfo.getTransactionName(), transactionId, 
                success ? "成功" : "失败", duration, transactionInfo.getStepCount());
    }

    /**
     * 记录事务异常
     *
     * @param transactionId 事务ID
     * @param exception 异常信息
     */
    public void recordTransactionException(String transactionId, Exception exception) {
        TransactionInfo transactionInfo = activeTransactions.get(transactionId);
        if (transactionInfo == null) {
            log.warn("未找到事务信息: {}", transactionId);
            return;
        }

        Span transactionSpan = transactionInfo.getTransactionSpan();
        transactionSpan.tag("error", "true");
        transactionSpan.tag("error.message", exception.getMessage());
        transactionSpan.tag("error.class", exception.getClass().getSimpleName());
        
        transactionInfo.setHasError(true);
        
        log.error("事务异常: 事务ID: {}, 异常: {}", transactionId, exception.getMessage(), exception);
    }

    /**
     * 获取活跃事务数量
     */
    public int getActiveTransactionCount() {
        return activeTransactions.size();
    }

    /**
     * 获取总事务数量
     */
    public long getTotalTransactionCount() {
        return transactionCounter.get();
    }

    /**
     * 清理超时事务
     */
    public void cleanupTimeoutTransactions() {
        long currentTime = System.currentTimeMillis();
        long timeoutThreshold = 30 * 60 * 1000; // 30分钟超时
        
        activeTransactions.entrySet().removeIf(entry -> {
            TransactionInfo info = entry.getValue();
            if (currentTime - info.getStartTime() > timeoutThreshold) {
                log.warn("清理超时事务: 事务ID: {}, 事务名称: {}, 超时时间: {}ms", 
                        entry.getKey(), info.getTransactionName(), 
                        currentTime - info.getStartTime());
                
                // 标记事务为超时并结束
                info.getTransactionSpan().tag("timeout", "true");
                info.getTransactionSpan().tag("error", "true");
                info.getTransactionSpan().end();
                
                return true;
            }
            return false;
        });
    }

    /**
     * 生成事务ID
     */
    private String generateTransactionId() {
        return "TXN-" + System.currentTimeMillis() + "-" + transactionCounter.incrementAndGet();
    }

    /**
     * 事务信息内部类
     */
    private static class TransactionInfo {
        private final String transactionId;
        private final String transactionName;
        private final String userId;
        private final long startTime;
        private final Span transactionSpan;
        private int stepCount = 0;
        private boolean hasError = false;

        public TransactionInfo(String transactionId, String transactionName, String userId, 
                             long startTime, Span transactionSpan) {
            this.transactionId = transactionId;
            this.transactionName = transactionName;
            this.userId = userId;
            this.startTime = startTime;
            this.transactionSpan = transactionSpan;
        }

        public String getTransactionId() { return transactionId; }
        public String getTransactionName() { return transactionName; }
        public String getUserId() { return userId; }
        public long getStartTime() { return startTime; }
        public Span getTransactionSpan() { return transactionSpan; }
        public int getStepCount() { return stepCount; }
        public boolean isHasError() { return hasError; }
        
        public void incrementStepCount() { this.stepCount++; }
        public void setHasError(boolean hasError) { this.hasError = hasError; }
    }
}