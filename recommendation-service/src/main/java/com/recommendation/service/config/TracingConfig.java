package com.recommendation.service.config;

import brave.sampler.Sampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.Span;
import zipkin2.reporter.Reporter;

/**
 * 分布式链路追踪配置
 */
@Configuration
public class TracingConfig {

    @Value("${spring.sleuth.sampler.probability:0.1}")
    private float samplerProbability;

    /**
     * 配置采样器
     * 采样率设置：0.1表示10%的请求会被追踪
     */
    @Bean
    public Sampler alwaysSampler() {
        return Sampler.create(samplerProbability);
    }

    /**
     * 自定义Span报告器（可选）
     * 用于自定义Span数据的处理逻辑
     */
    @Bean
    public Reporter<Span> spanReporter() {
        return new Reporter<Span>() {
            @Override
            public void report(Span span) {
                // 可以在这里添加自定义的Span处理逻辑
                // 例如：过滤敏感信息、添加额外标签等
                
                // 默认情况下，Span会被发送到Zipkin服务器
                // 这里可以添加日志记录
                if (span.duration() != null && span.duration() > 1000000) { // 超过1秒的请求
                    System.out.println("Slow request detected: " + span.name() + 
                                     " took " + (span.duration() / 1000) + "ms");
                }
            }
        };
    }
}