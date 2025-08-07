package com.recommendation.service.recovery;

import com.recommendation.service.health.HealthCheckService;
import com.recommendation.service.health.HealthStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自动恢复服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoRecoveryService {

    private final HealthCheckService healthCheckService;

    @Value("${recommendation.recovery.max-retry-count:3}")
    private int maxRetryCount;

    @Value("${recommendation.recovery.retry-interval:30000}")
    private long retryInterval;

    @Value("${recommendation.recovery.health-check-interval:10000}")
    private long healthCheckInterval;

    // 连续失败计数器
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    
    // 最后一次健康检查时间
    private final AtomicLong lastHealthCheckTime = new AtomicLong(0);
    
    // 服务状态
    private volatile boolean serviceHealthy = true;
    private volatile boolean autoRecoveryEnabled = true;

    /**
     * 定期健康检查和自动恢复
     */
    @Scheduled(fixedDelayString = "${recommendation.recovery.health-check-interval:10000}")
    public void performHealthCheckAndRecovery() {
        if (!autoRecoveryEnabled) {
            return;
        }

        try {
            long currentTime = System.currentTimeMillis();
            lastHealthCheckTime.set(currentTime);

            HealthStatus healthStatus = healthCheckService.getDetailedHealthStatus();
            
            if (healthStatus.isOverall()) {
                handleHealthyStatus();
            } else {
                handleUnhealthyStatus(healthStatus);
            }

        } catch (Exception e) {
            log.error("健康检查和自动恢复执行失败", e);
            handleException(e);
        }
    }

    /**
     * 处理健康状态
     */
    private void handleHealthyStatus() {
        if (!serviceHealthy) {
            log.info("服务恢复健康状态");
            serviceHealthy = true;
        }
        
        // 重置失败计数器
        int previousFailures = consecutiveFailures.getAndSet(0);
        if (previousFailures > 0) {
            log.info("服务已恢复，重置连续失败计数器，之前失败次数: {}", previousFailures);
        }
    }

    /**
     * 处理不健康状态
     */
    private void handleUnhealthyStatus(HealthStatus healthStatus) {
        int currentFailures = consecutiveFailures.incrementAndGet();
        serviceHealthy = false;
        
        log.warn("服务健康检查失败，连续失败次数: {}, 健康状态: {}", currentFailures, healthStatus);

        if (currentFailures <= maxRetryCount) {
            // 尝试自动恢复
            attemptAutoRecovery(healthStatus, currentFailures);
        } else {
            log.error("服务连续失败次数超过最大重试次数 {}, 停止自动恢复", maxRetryCount);
            // 可以在这里触发告警或其他处理
            triggerAlert(healthStatus, currentFailures);
        }
    }

    /**
     * 尝试自动恢复
     */
    private void attemptAutoRecovery(HealthStatus healthStatus, int attemptCount) {
        log.info("开始第 {} 次自动恢复尝试", attemptCount);

        try {
            // 根据具体的健康问题执行相应的恢复策略
            if (!healthStatus.isDatabase()) {
                recoverDatabaseConnection();
            }

            if (!healthStatus.isRedis()) {
                recoverRedisConnection();
            }

            if (!healthStatus.isSystem()) {
                recoverSystemResources();
            }

            // 等待一段时间后再次检查
            Thread.sleep(retryInterval);

            // 重新检查健康状态
            HealthStatus newHealthStatus = healthCheckService.getDetailedHealthStatus();
            if (newHealthStatus.isOverall()) {
                log.info("自动恢复成功，服务已恢复健康状态");
                handleHealthyStatus();
            } else {
                log.warn("自动恢复尝试 {} 失败，服务仍不健康", attemptCount);
            }

        } catch (Exception e) {
            log.error("自动恢复尝试 {} 执行失败", attemptCount, e);
        }
    }

    /**
     * 恢复数据库连接
     */
    private void recoverDatabaseConnection() {
        log.info("尝试恢复数据库连接");
        try {
            // 这里可以实现数据库连接池重置、重新连接等逻辑
            // 例如：重新初始化数据源、清理无效连接等
            
            // 简单的恢复策略：强制进行一次数据库健康检查
            boolean dbHealthy = healthCheckService.checkDatabaseHealth();
            log.info("数据库连接恢复尝试完成，当前状态: {}", dbHealthy ? "健康" : "不健康");
            
        } catch (Exception e) {
            log.error("数据库连接恢复失败", e);
        }
    }

    /**
     * 恢复Redis连接
     */
    private void recoverRedisConnection() {
        log.info("尝试恢复Redis连接");
        try {
            // 这里可以实现Redis连接重置、重新连接等逻辑
            // 例如：重新初始化Redis连接池、清理无效连接等
            
            // 简单的恢复策略：强制进行一次Redis健康检查
            boolean redisHealthy = healthCheckService.checkRedisHealth();
            log.info("Redis连接恢复尝试完成，当前状态: {}", redisHealthy ? "健康" : "不健康");
            
        } catch (Exception e) {
            log.error("Redis连接恢复失败", e);
        }
    }

    /**
     * 恢复系统资源
     */
    private void recoverSystemResources() {
        log.info("尝试恢复系统资源");
        try {
            // 执行垃圾回收
            System.gc();
            
            // 等待垃圾回收完成
            Thread.sleep(1000);
            
            // 检查内存使用情况
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryUsageRatio = (double) usedMemory / totalMemory;
            
            log.info("系统资源恢复尝试完成，当前内存使用率: {:.2f}%", memoryUsageRatio * 100);
            
        } catch (Exception e) {
            log.error("系统资源恢复失败", e);
        }
    }

    /**
     * 处理异常
     */
    private void handleException(Exception e) {
        int currentFailures = consecutiveFailures.incrementAndGet();
        serviceHealthy = false;
        
        log.error("健康检查执行异常，连续失败次数: {}", currentFailures, e);
        
        if (currentFailures > maxRetryCount) {
            triggerAlert(null, currentFailures);
        }
    }

    /**
     * 触发告警
     */
    private void triggerAlert(HealthStatus healthStatus, int failureCount) {
        log.error("触发服务告警 - 连续失败次数: {}, 健康状态: {}", failureCount, healthStatus);
        
        // 这里可以集成告警系统，如发送邮件、短信、钉钉消息等
        // 例如：
        // alertService.sendAlert("推荐服务健康检查失败", healthStatus, failureCount);
    }

    /**
     * 获取服务健康状态
     */
    public boolean isServiceHealthy() {
        return serviceHealthy;
    }

    /**
     * 获取连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 获取最后一次健康检查时间
     */
    public long getLastHealthCheckTime() {
        return lastHealthCheckTime.get();
    }

    /**
     * 启用/禁用自动恢复
     */
    public void setAutoRecoveryEnabled(boolean enabled) {
        this.autoRecoveryEnabled = enabled;
        log.info("自动恢复功能已{}", enabled ? "启用" : "禁用");
    }

    /**
     * 手动触发恢复
     */
    public void manualRecovery() {
        log.info("手动触发服务恢复");
        performHealthCheckAndRecovery();
    }

    /**
     * 重置失败计数器
     */
    public void resetFailureCounter() {
        int previousFailures = consecutiveFailures.getAndSet(0);
        log.info("手动重置失败计数器，之前失败次数: {}", previousFailures);
    }
}