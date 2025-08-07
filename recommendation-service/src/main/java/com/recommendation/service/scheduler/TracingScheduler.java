package com.recommendation.service.scheduler;

import com.recommendation.service.tracing.DistributedTransactionMonitor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 链路追踪定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TracingScheduler {

    private final DistributedTransactionMonitor transactionMonitor;

    /**
     * 定期清理超时事务
     * 每10分钟执行一次
     */
    @Scheduled(fixedRate = 600000) // 10分钟
    public void cleanupTimeoutTransactions() {
        try {
            log.debug("开始清理超时事务");
            
            int activeCountBefore = transactionMonitor.getActiveTransactionCount();
            transactionMonitor.cleanupTimeoutTransactions();
            int activeCountAfter = transactionMonitor.getActiveTransactionCount();
            
            int cleanedCount = activeCountBefore - activeCountAfter;
            if (cleanedCount > 0) {
                log.info("清理超时事务完成，清理数量: {}, 剩余活跃事务: {}", cleanedCount, activeCountAfter);
            } else {
                log.debug("清理超时事务完成，无超时事务");
            }
            
        } catch (Exception e) {
            log.error("清理超时事务失败", e);
        }
    }

    /**
     * 定期输出事务统计信息
     * 每30分钟执行一次
     */
    @Scheduled(fixedRate = 1800000) // 30分钟
    public void logTransactionStats() {
        try {
            int activeCount = transactionMonitor.getActiveTransactionCount();
            long totalCount = transactionMonitor.getTotalTransactionCount();
            
            log.info("事务统计信息 - 活跃事务数: {}, 总事务数: {}", activeCount, totalCount);
            
            // 如果活跃事务数过多，记录警告
            if (activeCount > 100) {
                log.warn("活跃事务数过多: {}, 可能存在事务泄漏", activeCount);
            }
            
        } catch (Exception e) {
            log.error("输出事务统计信息失败", e);
        }
    }
}