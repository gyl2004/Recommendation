package com.recommendation.service.tracing;

import brave.Span;
import brave.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 链路追踪工具类测试
 */
@ExtendWith(MockitoExtension.class)
class TracingUtilsTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private Tracer.SpanInScope spanInScope;

    private TracingUtils tracingUtils;

    @BeforeEach
    void setUp() {
        tracingUtils = new TracingUtils(tracer);
        
        // 设置默认的mock行为
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(span.tag(anyString(), anyString())).thenReturn(span);
        when(tracer.withSpanInScope(span)).thenReturn(spanInScope);
        when(tracer.currentSpan()).thenReturn(span);
    }

    @Test
    void testTraceSupplierSuccess() {
        // 准备测试数据
        String spanName = "test-operation";
        String expectedResult = "test-result";
        
        // 执行测试
        String result = tracingUtils.traceSupplier(spanName, () -> expectedResult);
        
        // 验证结果
        assertEquals(expectedResult, result);
        
        // 验证Span操作
        verify(tracer).nextSpan();
        verify(span).name(spanName);
        verify(span).start();
        verify(span).tag(eq("start.time"), anyString());
        verify(span).tag(eq("duration"), anyString());
        verify(span).end();
        verify(tracer).withSpanInScope(span);
    }

    @Test
    void testTraceSupplierWithTags() {
        // 准备测试数据
        String spanName = "test-operation";
        String expectedResult = "test-result";
        Map<String, String> tags = Map.of("key1", "value1", "key2", "value2");
        
        // 执行测试
        String result = tracingUtils.traceSupplier(spanName, () -> expectedResult, tags);
        
        // 验证结果
        assertEquals(expectedResult, result);
        
        // 验证标签被添加
        verify(span).tag("key1", "value1");
        verify(span).tag("key2", "value2");
    }

    @Test
    void testTraceSupplierWithException() {
        // 准备测试数据
        String spanName = "test-operation";
        RuntimeException expectedException = new RuntimeException("Test exception");
        
        // 执行测试并验证异常
        RuntimeException actualException = assertThrows(RuntimeException.class, () -> 
                tracingUtils.traceSupplier(spanName, () -> {
                    throw expectedException;
                }));
        
        // 验证异常
        assertEquals(expectedException, actualException);
        
        // 验证错误标签被添加
        verify(span).tag("error", "true");
        verify(span).tag("error.message", "Test exception");
        verify(span).tag("error.class", "RuntimeException");
        verify(span).end();
    }

    @Test
    void testTraceRunnable() {
        // 准备测试数据
        String spanName = "test-runnable";
        boolean[] executed = {false};
        
        // 执行测试
        tracingUtils.traceRunnable(spanName, () -> executed[0] = true);
        
        // 验证结果
        assertTrue(executed[0]);
        
        // 验证Span操作
        verify(tracer).nextSpan();
        verify(span).name(spanName);
        verify(span).start();
        verify(span).end();
    }

    @Test
    void testAddTag() {
        // 执行测试
        tracingUtils.addTag("test-key", "test-value");
        
        // 验证标签被添加
        verify(tracer).currentSpan();
        verify(span).tag("test-key", "test-value");
    }

    @Test
    void testAddTagWithNullSpan() {
        // 模拟没有当前Span的情况
        when(tracer.currentSpan()).thenReturn(null);
        
        // 执行测试（不应该抛出异常）
        assertDoesNotThrow(() -> tracingUtils.addTag("test-key", "test-value"));
        
        // 验证没有调用tag方法
        verify(span, never()).tag(anyString(), anyString());
    }

    @Test
    void testAddTags() {
        // 准备测试数据
        Map<String, String> tags = Map.of("key1", "value1", "key2", "value2");
        
        // 执行测试
        tracingUtils.addTags(tags);
        
        // 验证标签被添加
        verify(span).tag("key1", "value1");
        verify(span).tag("key2", "value2");
    }

    @Test
    void testAddEvent() {
        // 执行测试
        tracingUtils.addEvent("test-event");
        
        // 验证事件被添加
        verify(tracer).currentSpan();
        verify(span).annotate("test-event");
    }

    @Test
    void testGetCurrentTraceId() {
        // 模拟TraceContext
        brave.propagation.TraceContext traceContext = mock(brave.propagation.TraceContext.class);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn(12345L);
        
        // 执行测试
        String traceId = tracingUtils.getCurrentTraceId();
        
        // 验证结果
        assertEquals("12345", traceId);
    }

    @Test
    void testGetCurrentTraceIdWithNullSpan() {
        // 模拟没有当前Span的情况
        when(tracer.currentSpan()).thenReturn(null);
        
        // 执行测试
        String traceId = tracingUtils.getCurrentTraceId();
        
        // 验证结果
        assertNull(traceId);
    }

    @Test
    void testGetCurrentSpanId() {
        // 模拟TraceContext
        brave.propagation.TraceContext traceContext = mock(brave.propagation.TraceContext.class);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.spanId()).thenReturn(67890L);
        
        // 执行测试
        String spanId = tracingUtils.getCurrentSpanId();
        
        // 验证结果
        assertEquals("67890", spanId);
    }

    @Test
    void testRecordSlowOperation() {
        // 执行测试（慢操作）
        tracingUtils.recordSlowOperation("slow-operation", 2000, 1000);
        
        // 验证慢操作标签被添加
        verify(span).tag("slow.operation", "true");
        verify(span).tag("slow.operation.name", "slow-operation");
        verify(span).tag("slow.operation.duration", "2000ms");
        verify(span).tag("slow.operation.threshold", "1000ms");
    }

    @Test
    void testRecordSlowOperationNotSlow() {
        // 执行测试（非慢操作）
        tracingUtils.recordSlowOperation("fast-operation", 500, 1000);
        
        // 验证慢操作标签没有被添加
        verify(span, never()).tag(eq("slow.operation"), anyString());
    }

    @Test
    void testRecordMetric() {
        // 执行测试
        tracingUtils.recordMetric("test-metric", "test-value");
        
        // 验证指标标签被添加
        verify(span).tag("metric.test-metric", "test-value");
    }

    @Test
    void testRecordUserInfo() {
        // 执行测试
        tracingUtils.recordUserInfo("user123", "premium");
        
        // 验证用户信息标签被添加
        verify(span).tag("user.id", "user123");
        verify(span).tag("user.type", "premium");
    }

    @Test
    void testRecordUserInfoWithNullValues() {
        // 执行测试
        tracingUtils.recordUserInfo(null, null);
        
        // 验证没有添加null值的标签
        verify(span, never()).tag(eq("user.id"), isNull());
        verify(span, never()).tag(eq("user.type"), isNull());
    }

    @Test
    void testRecordRecommendationInfo() {
        // 执行测试
        tracingUtils.recordRecommendationInfo("article", "homepage", "v1.0");
        
        // 验证推荐信息标签被添加
        verify(span).tag("recommendation.content_type", "article");
        verify(span).tag("recommendation.scene", "homepage");
        verify(span).tag("recommendation.algorithm_version", "v1.0");
    }
}