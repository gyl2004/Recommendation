package com.recommendation.service.interceptor;

import com.recommendation.service.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 监控拦截器
 * 自动收集HTTP请求的监控指标
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitoringInterceptor implements HandlerInterceptor {

    private final MonitoringService monitoringService;
    private static final String START_TIME_ATTRIBUTE = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, 
                           Object handler) throws Exception {
        // 记录请求开始时间
        request.setAttribute(START_TIME_ATTRIBUTE, System.currentTimeMillis());
        
        // 记录请求计数
        String uri = request.getRequestURI();
        if (uri.contains("/recommend")) {
            monitoringService.recordRecommendationRequest();
        }
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                              Object handler, Exception ex) throws Exception {
        // 计算请求处理时间
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            String uri = request.getRequestURI();
            
            // 记录推荐请求的响应时间
            if (uri.contains("/recommend")) {
                monitoringService.recordRecommendationDuration(duration, TimeUnit.MILLISECONDS);
                
                // 记录错误请求
                if (response.getStatus() >= 400) {
                    String errorType = getErrorType(response.getStatus());
                    monitoringService.recordRecommendationError(errorType);
                }
            }
            
            log.debug("请求处理完成 - URI: {}, Duration: {}ms, Status: {}", 
                     uri, duration, response.getStatus());
        }
        
        // 记录异常
        if (ex != null) {
            monitoringService.recordRecommendationError("exception");
            log.error("请求处理异常", ex);
        }
    }

    /**
     * 根据HTTP状态码获取错误类型
     */
    private String getErrorType(int statusCode) {
        if (statusCode >= 400 && statusCode < 500) {
            return "client_error";
        } else if (statusCode >= 500) {
            return "server_error";
        }
        return "unknown_error";
    }
}