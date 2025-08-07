package com.recommendation.service.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 监控服务
 * 负责收集和记录各种业务和系统指标
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitoringService {

    private final MeterRegistry meterRegistry;
    private final Timer recommendationTimer;
    private final Counter recommendationRequestCounter;
    private final Counter recommendationErrorCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Timer recallTimer;
    private final Timer rankingTimer;

    /**
     * 记录推荐请求
     */
    public void recordRecommendationRequest() {
        recommendationRequestCounter.increment();
        log.debug("记录推荐请求计数");
    }

    /**
     * 记录推荐请求响应时间
     */
    public void recordRecommendationDuration(long duration, TimeUnit timeUnit) {
        recommendationTimer.record(duration, timeUnit);
        log.debug("记录推荐请求响应时间: {} {}", duration, timeUnit);
    }

    /**
     * 记录推荐请求错误
     */
    public void recordRecommendationError(String errorType) {
        recommendationErrorCounter.increment("error_type", errorType);
        log.warn("记录推荐请求错误: {}", errorType);
    }

    /**
     * 记录缓存命中
     */
    public void recordCacheHit(String cacheType) {
        cacheHitCounter.increment("cache_type", cacheType);
        log.debug("记录缓存命中: {}", cacheType);
    }

    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss(String cacheType) {
        cacheMissCounter.increment("cache_type", cacheType);
        log.debug("记录缓存未命中: {}", cacheType);
    }

    /**
     * 记录召回服务响应时间
     */
    public void recordRecallDuration(long duration, TimeUnit timeUnit, String recallType) {
        recallTimer.record(duration, timeUnit, "recall_type", recallType);
        log.debug("记录召回服务响应时间: {} {} for {}", duration, timeUnit, recallType);
    }

    /**
     * 记录排序服务响应时间
     */
    public void recordRankingDuration(long duration, TimeUnit timeUnit, String rankingType) {
        rankingTimer.record(duration, timeUnit, "ranking_type", rankingType);
        log.debug("记录排序服务响应时间: {} {} for {}", duration, timeUnit, rankingType);
    }

    /**
     * 记录自定义业务指标
     */
    public void recordCustomMetric(String metricName, double value, String... tags) {
        meterRegistry.gauge(metricName, value, tags);
        log.debug("记录自定义指标: {} = {}", metricName, value);
    }

    /**
     * 增加自定义计数器
     */
    public void incrementCustomCounter(String counterName, String... tags) {
        Counter.builder(counterName)
                .tags(tags)
                .register(meterRegistry)
                .increment();
        log.debug("增加自定义计数器: {}", counterName);
    }

    /**
     * 记录QPS指标
     */
    public void recordQPS(String serviceName, double qps) {
        meterRegistry.gauge("recommendation.qps", qps, "service", serviceName);
        log.debug("记录QPS指标: {} = {}", serviceName, qps);
    }

    /**
     * 记录系统资源使用率
     */
    public void recordSystemResourceUsage() {
        // CPU使用率
        double cpuUsage = getCpuUsage();
        meterRegistry.gauge("recommendation.system.cpu.usage", cpuUsage);

        // 内存使用率
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        double memoryUsage = (double) (totalMemory - freeMemory) / totalMemory * 100;
        meterRegistry.gauge("recommendation.system.memory.usage", memoryUsage);

        // 堆内存使用情况
        long maxMemory = runtime.maxMemory();
        double heapUsage = (double) (totalMemory - freeMemory) / maxMemory * 100;
        meterRegistry.gauge("recommendation.system.heap.usage", heapUsage);

        log.debug("记录系统资源使用率 - CPU: {}%, Memory: {}%, Heap: {}%", 
                 cpuUsage, memoryUsage, heapUsage);
    }

    /**
     * 记录数据库性能指标
     */
    public void recordDatabaseMetrics(String operation, long duration, boolean success) {
        // 数据库操作响应时间
        Timer.builder("recommendation.database.operation.duration")
                .tag("operation", operation)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(duration, TimeUnit.MILLISECONDS);

        // 数据库操作计数
        Counter.builder("recommendation.database.operation.total")
                .tag("operation", operation)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();

        log.debug("记录数据库性能指标 - Operation: {}, Duration: {}ms, Success: {}", 
                 operation, duration, success);
    }

    /**
     * 记录Redis性能指标
     */
    public void recordRedisMetrics(String operation, long duration, boolean success) {
        // Redis操作响应时间
        Timer.builder("recommendation.redis.operation.duration")
                .tag("operation", operation)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(duration, TimeUnit.MILLISECONDS);

        // Redis操作计数
        Counter.builder("recommendation.redis.operation.total")
                .tag("operation", operation)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .increment();

        log.debug("记录Redis性能指标 - Operation: {}, Duration: {}ms, Success: {}", 
                 operation, duration, success);
    }

    /**
     * 获取CPU使用率
     */
    private double getCpuUsage() {
        // 这里应该使用更精确的CPU监控方法
        // 暂时返回模拟数据
        return Math.random() * 100;
    }

    /**
     * 记录推荐算法性能指标
     */
    public void recordAlgorithmMetrics(String algorithmName, int candidateCount, 
                                     int resultCount, long duration) {
        // 算法处理时间
        Timer.builder("recommendation.algorithm.duration")
                .tag("algorithm", algorithmName)
                .register(meterRegistry)
                .record(duration, TimeUnit.MILLISECONDS);

        // 候选集大小
        meterRegistry.gauge("recommendation.algorithm.candidate.count", candidateCount, 
                           "algorithm", algorithmName);

        // 结果集大小
        meterRegistry.gauge("recommendation.algorithm.result.count", resultCount, 
                           "algorithm", algorithmName);

        log.debug("记录算法性能指标 - Algorithm: {}, Candidates: {}, Results: {}, Duration: {}ms", 
                 algorithmName, candidateCount, resultCount, duration);
    }
}