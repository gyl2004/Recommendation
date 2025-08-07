package com.recommendation.service.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;

/**
 * Prometheus监控配置
 */
@Configuration
public class MonitoringConfig {

    /**
     * 自定义MeterRegistry配置
     */
    @Bean
    MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags("application", "recommendation-service");
    }

    /**
     * 推荐请求响应时间计时器
     */
    @Bean
    public Timer recommendationTimer(MeterRegistry meterRegistry) {
        return Timer.builder("recommendation.request.duration")
                .description("推荐请求响应时间")
                .tag("service", "recommendation")
                .register(meterRegistry);
    }

    /**
     * 推荐请求总数计数器
     */
    @Bean
    public Counter recommendationRequestCounter(MeterRegistry meterRegistry) {
        return Counter.builder("recommendation.request.total")
                .description("推荐请求总数")
                .tag("service", "recommendation")
                .register(meterRegistry);
    }

    /**
     * 推荐请求错误计数器
     */
    @Bean
    public Counter recommendationErrorCounter(MeterRegistry meterRegistry) {
        return Counter.builder("recommendation.request.errors")
                .description("推荐请求错误数")
                .tag("service", "recommendation")
                .register(meterRegistry);
    }

    /**
     * 缓存命中率计数器
     */
    @Bean
    public Counter cacheHitCounter(MeterRegistry meterRegistry) {
        return Counter.builder("recommendation.cache.hits")
                .description("缓存命中次数")
                .tag("service", "recommendation")
                .register(meterRegistry);
    }

    /**
     * 缓存未命中计数器
     */
    @Bean
    public Counter cacheMissCounter(MeterRegistry meterRegistry) {
        return Counter.builder("recommendation.cache.misses")
                .description("缓存未命中次数")
                .tag("service", "recommendation")
                .register(meterRegistry);
    }

    /**
     * 召回服务响应时间计时器
     */
    @Bean
    public Timer recallTimer(MeterRegistry meterRegistry) {
        return Timer.builder("recommendation.recall.duration")
                .description("召回服务响应时间")
                .tag("service", "recall")
                .register(meterRegistry);
    }

    /**
     * 排序服务响应时间计时器
     */
    @Bean
    public Timer rankingTimer(MeterRegistry meterRegistry) {
        return Timer.builder("recommendation.ranking.duration")
                .description("排序服务响应时间")
                .tag("service", "ranking")
                .register(meterRegistry);
    }

    /**
     * 数据库连接池监控
     */
    @Bean
    public Gauge dbConnectionPoolGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("recommendation.db.connection.pool.active")
                .description("数据库连接池活跃连接数")
                .tag("service", "database")
                .register(meterRegistry, this, obj -> getCurrentActiveConnections());
    }

    /**
     * Redis连接池监控
     */
    @Bean
    public Gauge redisConnectionPoolGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("recommendation.redis.connection.pool.active")
                .description("Redis连接池活跃连接数")
                .tag("service", "redis")
                .register(meterRegistry, this, obj -> getCurrentRedisConnections());
    }

    /**
     * JVM内存使用率监控
     */
    @Bean
    public Gauge jvmMemoryGauge(MeterRegistry meterRegistry) {
        return Gauge.builder("recommendation.jvm.memory.usage")
                .description("JVM内存使用率")
                .tag("service", "jvm")
                .register(meterRegistry, this, obj -> getJvmMemoryUsage());
    }

    // 获取当前数据库活跃连接数的方法
    private double getCurrentActiveConnections() {
        // 这里应该从实际的连接池获取数据
        // 暂时返回模拟数据
        return Math.random() * 10;
    }

    // 获取当前Redis连接数的方法
    private double getCurrentRedisConnections() {
        // 这里应该从实际的Redis连接池获取数据
        // 暂时返回模拟数据
        return Math.random() * 5;
    }

    // 获取JVM内存使用率的方法
    private double getJvmMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return (double) (totalMemory - freeMemory) / totalMemory * 100;
    }
}