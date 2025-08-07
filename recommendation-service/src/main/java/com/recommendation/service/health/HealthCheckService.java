package com.recommendation.service.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;

/**
 * 健康检查服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService implements HealthIndicator {

    private final DataSource dataSource;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${recommendation.health.timeout:3000}")
    private long healthCheckTimeout;

    @Override
    public Health health() {
        try {
            // 检查数据库连接
            boolean dbHealthy = checkDatabaseHealth();
            
            // 检查Redis连接
            boolean redisHealthy = checkRedisHealth();
            
            // 检查系统资源
            boolean systemHealthy = checkSystemHealth();
            
            if (dbHealthy && redisHealthy && systemHealthy) {
                return Health.up()
                        .withDetail("database", "UP")
                        .withDetail("redis", "UP")
                        .withDetail("system", "UP")
                        .withDetail("timestamp", System.currentTimeMillis())
                        .build();
            } else {
                return Health.down()
                        .withDetail("database", dbHealthy ? "UP" : "DOWN")
                        .withDetail("redis", redisHealthy ? "UP" : "DOWN")
                        .withDetail("system", systemHealthy ? "UP" : "DOWN")
                        .withDetail("timestamp", System.currentTimeMillis())
                        .build();
            }
            
        } catch (Exception e) {
            log.error("健康检查执行失败", e);
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .withDetail("timestamp", System.currentTimeMillis())
                    .build();
        }
    }

    /**
     * 检查数据库健康状态
     */
    public boolean checkDatabaseHealth() {
        try {
            long startTime = System.currentTimeMillis();
            
            try (Connection connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid((int) TimeUnit.MILLISECONDS.toSeconds(healthCheckTimeout));
                
                long responseTime = System.currentTimeMillis() - startTime;
                log.debug("数据库健康检查完成 - 响应时间: {}ms, 状态: {}", responseTime, isValid);
                
                return isValid && responseTime < healthCheckTimeout;
            }
            
        } catch (Exception e) {
            log.error("数据库健康检查失败", e);
            return false;
        }
    }

    /**
     * 检查Redis健康状态
     */
    public boolean checkRedisHealth() {
        try {
            long startTime = System.currentTimeMillis();
            
            String testKey = "health_check_" + System.currentTimeMillis();
            String testValue = "test";
            
            // 写入测试数据
            redisTemplate.opsForValue().set(testKey, testValue, 10, TimeUnit.SECONDS);
            
            // 读取测试数据
            String retrievedValue = (String) redisTemplate.opsForValue().get(testKey);
            
            // 清理测试数据
            redisTemplate.delete(testKey);
            
            long responseTime = System.currentTimeMillis() - startTime;
            boolean isHealthy = testValue.equals(retrievedValue) && responseTime < healthCheckTimeout;
            
            log.debug("Redis健康检查完成 - 响应时间: {}ms, 状态: {}", responseTime, isHealthy);
            
            return isHealthy;
            
        } catch (Exception e) {
            log.error("Redis健康检查失败", e);
            return false;
        }
    }

    /**
     * 检查系统健康状态
     */
    public boolean checkSystemHealth() {
        try {
            Runtime runtime = Runtime.getRuntime();
            
            // 检查内存使用率
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            double memoryUsageRatio = (double) usedMemory / totalMemory;
            
            // 检查可用处理器数量
            int availableProcessors = runtime.availableProcessors();
            
            // 内存使用率超过90%认为不健康
            boolean memoryHealthy = memoryUsageRatio < 0.9;
            
            // 至少需要1个可用处理器
            boolean processorHealthy = availableProcessors > 0;
            
            log.debug("系统健康检查完成 - 内存使用率: {:.2f}%, 可用处理器: {}, 内存健康: {}, 处理器健康: {}", 
                    memoryUsageRatio * 100, availableProcessors, memoryHealthy, processorHealthy);
            
            return memoryHealthy && processorHealthy;
            
        } catch (Exception e) {
            log.error("系统健康检查失败", e);
            return false;
        }
    }

    /**
     * 获取详细的健康状态信息
     */
    public HealthStatus getDetailedHealthStatus() {
        boolean dbHealthy = checkDatabaseHealth();
        boolean redisHealthy = checkRedisHealth();
        boolean systemHealthy = checkSystemHealth();
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        return HealthStatus.builder()
                .overall(dbHealthy && redisHealthy && systemHealthy)
                .database(dbHealthy)
                .redis(redisHealthy)
                .system(systemHealthy)
                .totalMemory(totalMemory)
                .usedMemory(usedMemory)
                .freeMemory(freeMemory)
                .availableProcessors(runtime.availableProcessors())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}