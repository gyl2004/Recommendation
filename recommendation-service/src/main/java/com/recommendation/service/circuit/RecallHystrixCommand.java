package com.recommendation.service.circuit;

import com.netflix.hystrix.HystrixCommandProperties;
import com.recommendation.service.service.FallbackService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Supplier;

/**
 * 召回服务熔断器命令
 */
@Slf4j
public class RecallHystrixCommand extends BaseHystrixCommand<List<Long>> {

    private final Supplier<List<Long>> businessLogic;
    private final FallbackService fallbackService;
    private final String userId;
    private final String contentType;
    private final Integer size;

    public RecallHystrixCommand(Supplier<List<Long>> businessLogic,
                               FallbackService fallbackService,
                               String userId,
                               String contentType,
                               Integer size) {
        super("RecallGroup", "GetRecallCandidates");
        this.businessLogic = businessLogic;
        this.fallbackService = fallbackService;
        this.userId = userId;
        this.contentType = contentType;
        this.size = size;
    }

    @Override
    protected HystrixCommandProperties.Setter getCommandProperties() {
        return HystrixCommandProperties.Setter()
                .withCircuitBreakerErrorThresholdPercentage(60)
                .withCircuitBreakerRequestVolumeThreshold(10)
                .withCircuitBreakerSleepWindowInMilliseconds(3000)
                .withExecutionTimeoutInMilliseconds(2000)
                .withExecutionTimeoutEnabled(true)
                .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);
    }

    @Override
    protected List<Long> run() throws Exception {
        log.debug("Executing recall business logic for user: {}", userId);
        return businessLogic.get();
    }

    @Override
    protected List<Long> getFallback() {
        log.warn("Recall service fallback triggered for user: {}, reason: {}", 
                userId, getFailedExecutionException() != null ? 
                getFailedExecutionException().getMessage() : "Circuit breaker open");
        
        return fallbackService.getRecallFallback(userId, contentType, size);
    }
}