package com.recommendation.service.circuit;

import com.netflix.hystrix.HystrixCommandProperties;
import com.recommendation.service.dto.RecommendResponse;
import com.recommendation.service.service.FallbackService;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 推荐服务熔断器命令
 */
@Slf4j
public class RecommendationHystrixCommand extends BaseHystrixCommand<RecommendResponse> {

    private final Supplier<RecommendResponse> businessLogic;
    private final FallbackService fallbackService;
    private final String userId;
    private final String contentType;
    private final Integer size;

    public RecommendationHystrixCommand(Supplier<RecommendResponse> businessLogic,
                                      FallbackService fallbackService,
                                      String userId,
                                      String contentType,
                                      Integer size) {
        super("RecommendationGroup", "GetRecommendation");
        this.businessLogic = businessLogic;
        this.fallbackService = fallbackService;
        this.userId = userId;
        this.contentType = contentType;
        this.size = size;
    }

    @Override
    protected HystrixCommandProperties.Setter getCommandProperties() {
        return HystrixCommandProperties.Setter()
                .withCircuitBreakerErrorThresholdPercentage(50)
                .withCircuitBreakerRequestVolumeThreshold(20)
                .withCircuitBreakerSleepWindowInMilliseconds(5000)
                .withExecutionTimeoutInMilliseconds(3000)
                .withExecutionTimeoutEnabled(true)
                .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);
    }

    @Override
    protected RecommendResponse run() throws Exception {
        log.debug("Executing recommendation business logic for user: {}", userId);
        return businessLogic.get();
    }

    @Override
    protected RecommendResponse getFallback() {
        log.warn("Recommendation service fallback triggered for user: {}, reason: {}", 
                userId, getFailedExecutionException() != null ? 
                getFailedExecutionException().getMessage() : "Circuit breaker open");
        
        // 根据失败原因选择不同的降级策略
        if (isCircuitBreakerOpen()) {
            return fallbackService.getCircuitBreakerFallback(userId, contentType, size);
        } else if (isResponseTimedOut()) {
            return fallbackService.getTimeoutFallback(userId, contentType, size);
        } else {
            return fallbackService.getDefaultFallback(userId, contentType, size);
        }
    }
}