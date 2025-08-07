package com.recommendation.service.circuit;

import com.netflix.hystrix.HystrixCommandProperties;
import com.recommendation.service.service.FallbackService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;

/**
 * 排序服务熔断器命令
 */
@Slf4j
public class RankingHystrixCommand extends BaseHystrixCommand<List<Long>> {

    private final Function<List<Long>, List<Long>> businessLogic;
    private final FallbackService fallbackService;
    private final List<Long> candidates;
    private final String userId;

    public RankingHystrixCommand(Function<List<Long>, List<Long>> businessLogic,
                                FallbackService fallbackService,
                                List<Long> candidates,
                                String userId) {
        super("RankingGroup", "RankCandidates");
        this.businessLogic = businessLogic;
        this.fallbackService = fallbackService;
        this.candidates = candidates;
        this.userId = userId;
    }

    @Override
    protected HystrixCommandProperties.Setter getCommandProperties() {
        return HystrixCommandProperties.Setter()
                .withCircuitBreakerErrorThresholdPercentage(40)
                .withCircuitBreakerRequestVolumeThreshold(15)
                .withCircuitBreakerSleepWindowInMilliseconds(4000)
                .withExecutionTimeoutInMilliseconds(1500)
                .withExecutionTimeoutEnabled(true)
                .withExecutionIsolationStrategy(HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);
    }

    @Override
    protected List<Long> run() throws Exception {
        log.debug("Executing ranking business logic for user: {}", userId);
        return businessLogic.apply(candidates);
    }

    @Override
    protected List<Long> getFallback() {
        log.warn("Ranking service fallback triggered for user: {}, reason: {}", 
                userId, getFailedExecutionException() != null ? 
                getFailedExecutionException().getMessage() : "Circuit breaker open");
        
        return fallbackService.getRankingFallback(candidates, userId);
    }
}