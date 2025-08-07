package com.recommendation.service.config;

import com.recommendation.service.interceptor.MonitoringInterceptor;
import com.recommendation.service.tracing.TracingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web配置类
 * 注册拦截器和其他Web相关配置
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final MonitoringInterceptor monitoringInterceptor;
    private final TracingInterceptor tracingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册链路追踪拦截器（优先级最高）
        registry.addInterceptor(tracingInterceptor)
                .addPathPatterns("/api/**")  // 追踪所有API请求
                .excludePathPatterns("/actuator/**")  // 排除actuator端点
                .order(1);
        
        // 注册监控拦截器
        registry.addInterceptor(monitoringInterceptor)
                .addPathPatterns("/api/**")  // 监控所有API请求
                .excludePathPatterns("/actuator/**")  // 排除actuator端点
                .order(2);
    }
}