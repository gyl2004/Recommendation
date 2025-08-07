package com.recommendation.service.config;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.netflix.hystrix.HystrixThreadPoolProperties;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.netflix.hystrix.dashboard.EnableHystrixDashboard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hystrix熔断器配置
 */
@Configuration
@EnableHystrix
@EnableHystrixDashboard
public class HystrixConfig {

    /**
     * 推荐服务熔断器配置
     */
    @Bean
    public HystrixCommandProperties.Setter recommendationCommandProperties() {
        return HystrixCommandProperties.Setter()
                // 熔断器开启的错误百分比阈值
                .withCircuitBreakerErrorThresholdPercentage(50)
                // 熔断器开启的最小请求数
                .withCircuitBreakerRequestVolumeThreshold(20)
                // 熔断器开启后的休眠时间窗口
                .withCircuitBreakerSleepWindowInMilliseconds(5000)
                // 执行超时时间
                .withExecutionTimeoutInMilliseconds(3000)
                // 是否启用超时
                .withExecutionTimeoutEnabled(true)
                // 隔离策略：线程池隔离
                .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);
    }

    /**
     * 召回服务熔断器配置
     */
    @Bean
    public HystrixCommandProperties.Setter recallCommandProperties() {
        return HystrixCommandProperties.Setter()
                .withCircuitBreakerErrorThresholdPercentage(60)
                .withCircuitBreakerRequestVolumeThreshold(10)
                .withCircuitBreakerSleepWindowInMilliseconds(3000)
                .withExecutionTimeoutInMilliseconds(2000)
                .withExecutionTimeoutEnabled(true)
                .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);
    }

    /**
     * 排序服务熔断器配置
     */
    @Bean
    public HystrixCommandProperties.Setter rankingCommandProperties() {
        return HystrixCommandProperties.Setter()
                .withCircuitBreakerErrorThresholdPercentage(40)
                .withCircuitBreakerRequestVolumeThreshold(15)
                .withCircuitBreakerSleepWindowInMilliseconds(4000)
                .withExecutionTimeoutInMilliseconds(1500)
                .withExecutionTimeoutEnabled(true)
                .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);
    }

    /**
     * 线程池配置
     */
    @Bean
    public HystrixThreadPoolProperties.Setter threadPoolProperties() {
        return HystrixThreadPoolProperties.Setter()
                // 核心线程数
                .withCoreSize(10)
                // 最大线程数
                .withMaximumSize(20)
                // 队列大小
                .withMaxQueueSize(100)
                // 队列拒绝阈值
                .withQueueSizeRejectionThreshold(80)
                // 线程存活时间
                .withKeepAliveTimeMinutes(2);
    }
}