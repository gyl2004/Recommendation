package com.recommendation.service.tracing;

import brave.Span;
import brave.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 链路追踪工具类
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TracingUtils {

    private final Tracer tracer;

    /**
     * 创建新的Span并执行业务逻辑
     *
     * @param spanName Span名称
     * @param supplier 业务逻辑
     * @param <T> 返回类型
     * @return 业务逻辑执行结果
     */
    public <T> T traceSupplier(String spanName, Supplier<T> supplier) {
        return traceSupplier(spanName, supplier, null);
    }

    /**
     * 创建新的Span并执行业务逻辑，支持添加自定义标签
     *
     * @param spanName Span名称
     * @param supplier 业务逻辑
     * @param tags 自定义标签
     * @param <T> 返回类型
     * @return 业务逻辑执行结果
     */
    public <T> T traceSupplier(String spanName, Supplier<T> supplier, Map<String, String> tags) {
        Span span = tracer.nextSpan().name(spanName).start();
        
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            // 添加自定义标签
            if (tags != null) {
                tags.forEach(span::tag);
            }
            
            // 记录开始时间
            long startTime = System.currentTimeMillis();
            span.tag("start.time", String.valueOf(startTime));
            
            log.debug("开始执行Span: {}, TraceId: {}, SpanId: {}", 
                    spanName, span.context().traceId(), span.context().spanId());
            
            // 执行业务逻辑
            T result = supplier.get();
            
            // 记录执行时间
            long duration = System.currentTimeMillis() - startTime;
            span.tag("duration", duration + "ms");
            
            // 如果执行时间过长，添加慢操作标签
            if (duration > 500) {
                span.tag("slow.operation", "true");
            }
            
            log.debug("完成执行Span: {}, 耗时: {}ms, TraceId: {}, SpanId: {}", 
                    spanName, duration, span.context().traceId(), span.context().spanId());
            
            return result;
            
        } catch (Exception e) {
            // 记录异常信息
            span.tag("error", "true");
            span.tag("error.message", e.getMessage());
            span.tag("error.class", e.getClass().getSimpleName());
            
            log.error("Span执行异常: {}, TraceId: {}, SpanId: {}, 错误: {}", 
                    spanName, span.context().traceId(), span.context().spanId(), e.getMessage(), e);
            
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * 执行无返回值的业务逻辑
     *
     * @param spanName Span名称
     * @param runnable 业务逻辑
     */
    public void traceRunnable(String spanName, Runnable runnable) {
        traceRunnable(spanName, runnable, null);
    }

    /**
     * 执行无返回值的业务逻辑，支持添加自定义标签
     *
     * @param spanName Span名称
     * @param runnable 业务逻辑
     * @param tags 自定义标签
     */
    public void traceRunnable(String spanName, Runnable runnable, Map<String, String> tags) {
        traceSupplier(spanName, () -> {
            runnable.run();
            return null;
        }, tags);
    }

    /**
     * 为当前Span添加标签
     *
     * @param key 标签键
     * @param value 标签值
     */
    public void addTag(String key, String value) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag(key, value);
        }
    }

    /**
     * 为当前Span添加多个标签
     *
     * @param tags 标签Map
     */
    public void addTags(Map<String, String> tags) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null && tags != null) {
            tags.forEach(currentSpan::tag);
        }
    }

    /**
     * 为当前Span添加事件
     *
     * @param event 事件名称
     */
    public void addEvent(String event) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.annotate(event);
        }
    }

    /**
     * 获取当前TraceId
     *
     * @return TraceId字符串，如果没有当前Span则返回null
     */
    public String getCurrentTraceId() {
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().traceId() + "" : null;
    }

    /**
     * 获取当前SpanId
     *
     * @return SpanId字符串，如果没有当前Span则返回null
     */
    public String getCurrentSpanId() {
        Span currentSpan = tracer.currentSpan();
        return currentSpan != null ? currentSpan.context().spanId() + "" : null;
    }

    /**
     * 记录慢操作
     *
     * @param operationName 操作名称
     * @param duration 执行时间（毫秒）
     * @param threshold 慢操作阈值（毫秒）
     */
    public void recordSlowOperation(String operationName, long duration, long threshold) {
        if (duration > threshold) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null) {
                currentSpan.tag("slow.operation", "true");
                currentSpan.tag("slow.operation.name", operationName);
                currentSpan.tag("slow.operation.duration", duration + "ms");
                currentSpan.tag("slow.operation.threshold", threshold + "ms");
            }
            
            log.warn("检测到慢操作: {}, 耗时: {}ms, 阈值: {}ms, TraceId: {}", 
                    operationName, duration, threshold, getCurrentTraceId());
        }
    }

    /**
     * 记录业务指标
     *
     * @param metricName 指标名称
     * @param value 指标值
     */
    public void recordMetric(String metricName, String value) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            currentSpan.tag("metric." + metricName, value);
        }
    }

    /**
     * 记录用户相关信息
     *
     * @param userId 用户ID
     * @param userType 用户类型
     */
    public void recordUserInfo(String userId, String userType) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            if (userId != null) {
                currentSpan.tag("user.id", userId);
            }
            if (userType != null) {
                currentSpan.tag("user.type", userType);
            }
        }
    }

    /**
     * 记录推荐相关信息
     *
     * @param contentType 内容类型
     * @param scene 推荐场景
     * @param algorithmVersion 算法版本
     */
    public void recordRecommendationInfo(String contentType, String scene, String algorithmVersion) {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            if (contentType != null) {
                currentSpan.tag("recommendation.content_type", contentType);
            }
            if (scene != null) {
                currentSpan.tag("recommendation.scene", scene);
            }
            if (algorithmVersion != null) {
                currentSpan.tag("recommendation.algorithm_version", algorithmVersion);
            }
        }
    }
}