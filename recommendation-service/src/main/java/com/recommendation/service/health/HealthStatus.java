package com.recommendation.service.health;

import lombok.Builder;
import lombok.Data;

/**
 * 健康状态数据类
 */
@Data
@Builder
public class HealthStatus {
    
    /**
     * 整体健康状态
     */
    private boolean overall;
    
    /**
     * 数据库健康状态
     */
    private boolean database;
    
    /**
     * Redis健康状态
     */
    private boolean redis;
    
    /**
     * 系统健康状态
     */
    private boolean system;
    
    /**
     * 总内存
     */
    private long totalMemory;
    
    /**
     * 已使用内存
     */
    private long usedMemory;
    
    /**
     * 可用内存
     */
    private long freeMemory;
    
    /**
     * 可用处理器数量
     */
    private int availableProcessors;
    
    /**
     * 检查时间戳
     */
    private long timestamp;
    
    /**
     * 获取内存使用率
     */
    public double getMemoryUsageRatio() {
        return totalMemory > 0 ? (double) usedMemory / totalMemory : 0.0;
    }
    
    /**
     * 获取内存使用率百分比
     */
    public double getMemoryUsagePercentage() {
        return getMemoryUsageRatio() * 100;
    }
}