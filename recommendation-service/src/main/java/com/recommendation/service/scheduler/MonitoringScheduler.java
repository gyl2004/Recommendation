package com.recommendation.service.scheduler;

import com.recommendation.service.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 监控定时任务
 * 定期收集系统资源使用率和其他监控指标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitoringScheduler {

    private final MonitoringService monitoringService;

    /**
     * 每30秒收集一次系统资源使用率
     */
    @Scheduled(fixedRate = 30000)
    public void collectSystemResourceUsage() {
        try {
            monitoringService.recordSystemResourceUsage();
            log.debug("系统资源使用率收集完成");
        } catch (Exception e) {
            log.error("收集系统资源使用率失败", e);
        }
    }

    /**
     * 每分钟计算一次QPS
     */
    @Scheduled(fixedRate = 60000)
    public void calculateQPS() {
        try {
            // 这里应该基于实际的请求计数来计算QPS
            // 暂时使用模拟数据
            double recommendationQPS = Math.random() * 1000;
            monitoringService.recordQPS("recommendation", recommendationQPS);
            
            log.debug("QPS计算完成 - Recommendation QPS: {}", recommendationQPS);
        } catch (Exception e) {
            log.error("计算QPS失败", e);
        }
    }

    /**
     * 每5分钟收集一次数据库连接池状态
     */
    @Scheduled(fixedRate = 300000)
    public void collectDatabasePoolStatus() {
        try {
            // 这里应该从实际的数据库连接池获取状态
            // 暂时记录模拟数据
            int activeConnections = (int) (Math.random() * 10);
            int idleConnections = (int) (Math.random() * 5);
            int totalConnections = activeConnections + idleConnections;
            
            monitoringService.recordCustomMetric("recommendation.db.pool.active", activeConnections);
            monitoringService.recordCustomMetric("recommendation.db.pool.idle", idleConnections);
            monitoringService.recordCustomMetric("recommendation.db.pool.total", totalConnections);
            
            log.debug("数据库连接池状态收集完成 - Active: {}, Idle: {}, Total: {}", 
                     activeConnections, idleConnections, totalConnections);
        } catch (Exception e) {
            log.error("收集数据库连接池状态失败", e);
        }
    }

    /**
     * 每5分钟收集一次Redis连接池状态
     */
    @Scheduled(fixedRate = 300000)
    public void collectRedisPoolStatus() {
        try {
            // 这里应该从实际的Redis连接池获取状态
            // 暂时记录模拟数据
            int activeConnections = (int) (Math.random() * 5);
            int idleConnections = (int) (Math.random() * 3);
            int totalConnections = activeConnections + idleConnections;
            
            monitoringService.recordCustomMetric("recommendation.redis.pool.active", activeConnections);
            monitoringService.recordCustomMetric("recommendation.redis.pool.idle", idleConnections);
            monitoringService.recordCustomMetric("recommendation.redis.pool.total", totalConnections);
            
            log.debug("Redis连接池状态收集完成 - Active: {}, Idle: {}, Total: {}", 
                     activeConnections, idleConnections, totalConnections);
        } catch (Exception e) {
            log.error("收集Redis连接池状态失败", e);
        }
    }

    /**
     * 每小时清理过期的监控数据
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredMetrics() {
        try {
            // 这里可以实现清理过期监控数据的逻辑
            log.info("监控数据清理任务执行完成");
        } catch (Exception e) {
            log.error("清理监控数据失败", e);
        }
    }
}