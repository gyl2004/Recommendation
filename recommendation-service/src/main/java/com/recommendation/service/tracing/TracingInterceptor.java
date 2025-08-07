package com.recommendation.service.tracing;

import brave.Span;
import brave.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 链路追踪拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TracingInterceptor implements HandlerInterceptor {

    private final Tracer tracer;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 获取当前Span
        Span currentSpan = tracer.nextSpan();
        
        if (currentSpan != null) {
            // 添加自定义标签
            currentSpan.tag("http.method", request.getMethod());
            currentSpan.tag("http.url", request.getRequestURL().toString());
            currentSpan.tag("user.agent", request.getHeader("User-Agent"));
            
            // 添加用户ID（如果存在）
            String userId = request.getParameter("userId");
            if (userId != null) {
                currentSpan.tag("user.id", userId);
            }
            
            // 添加内容类型（如果存在）
            String contentType = request.getParameter("contentType");
            if (contentType != null) {
                currentSpan.tag("content.type", contentType);
            }
            
            // 添加请求场景（如果存在）
            String scene = request.getParameter("scene");
            if (scene != null) {
                currentSpan.tag("request.scene", scene);
            }
            
            // 记录请求开始时间
            request.setAttribute("span.start.time", System.currentTimeMillis());
            
            log.debug("开始追踪请求: {} {}, TraceId: {}, SpanId: {}", 
                    request.getMethod(), request.getRequestURI(),
                    currentSpan.context().traceId(), currentSpan.context().spanId());
        }
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                               Object handler, Exception ex) {
        Span currentSpan = tracer.currentSpan();
        
        if (currentSpan != null) {
            // 添加响应状态码
            currentSpan.tag("http.status_code", String.valueOf(response.getStatus()));
            
            // 计算请求处理时间
            Long startTime = (Long) request.getAttribute("span.start.time");
            if (startTime != null) {
                long duration = System.currentTimeMillis() - startTime;
                currentSpan.tag("request.duration", duration + "ms");
                
                // 如果请求时间过长，添加慢请求标签
                if (duration > 1000) {
                    currentSpan.tag("slow.request", "true");
                }
            }
            
            // 如果有异常，记录异常信息
            if (ex != null) {
                currentSpan.tag("error", "true");
                currentSpan.tag("error.message", ex.getMessage());
                currentSpan.tag("error.class", ex.getClass().getSimpleName());
            }
            
            // 根据响应状态码判断是否为错误
            if (response.getStatus() >= 400) {
                currentSpan.tag("error", "true");
                currentSpan.tag("error.status", String.valueOf(response.getStatus()));
            }
            
            log.debug("完成追踪请求: {} {}, 状态码: {}, TraceId: {}, SpanId: {}", 
                    request.getMethod(), request.getRequestURI(), response.getStatus(),
                    currentSpan.context().traceId(), currentSpan.context().spanId());
        }
    }
}