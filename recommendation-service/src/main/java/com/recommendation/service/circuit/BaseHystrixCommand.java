package com.recommendation.service.circuit;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.HystrixThreadPoolKey;
import lombok.extern.slf4j.Slf4j;

/**
 * Hystrix命令基类
 */
@Slf4j
public abstract class BaseHystrixCommand<T> extends HystrixCommand<T> {

    protected BaseHystrixCommand(String groupKey, String commandKey, String threadPoolKey) {
        super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
                .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
                .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(threadPoolKey))
                .andCommandPropertiesDefaults(getCommandProperties()));
    }

    protected BaseHystrixCommand(String groupKey, String commandKey) {
        this(groupKey, commandKey, groupKey + "-pool");
    }

    /**
     * 获取命令属性配置
     */
    protected abstract HystrixCommandProperties.Setter getCommandProperties();

    /**
     * 执行业务逻辑
     */
    @Override
    protected abstract T run() throws Exception;

    /**
     * 降级逻辑
     */
    @Override
    protected abstract T getFallback();

    /**
     * 记录执行结果
     */
    @Override
    protected void onSuccess() {
        log.debug("Hystrix command {} executed successfully", getCommandKey().name());
    }

    @Override
    protected void onFailure() {
        log.warn("Hystrix command {} failed, fallback executed", getCommandKey().name());
    }

    @Override
    protected void onTimeout() {
        log.warn("Hystrix command {} timed out, fallback executed", getCommandKey().name());
    }
}