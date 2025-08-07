package com.recommendation.user.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 */
@Configuration
public class RabbitMQConfig {

    // 用户行为相关队列和交换机
    public static final String USER_BEHAVIOR_QUEUE = "user.behavior.queue";
    public static final String USER_BEHAVIOR_EXCHANGE = "user.behavior.exchange";
    public static final String USER_BEHAVIOR_ROUTING_KEY = "user.behavior.routing.key";

    // 用户画像更新相关队列和交换机
    public static final String USER_PROFILE_QUEUE = "user.profile.queue";
    public static final String USER_PROFILE_EXCHANGE = "user.profile.exchange";
    public static final String USER_PROFILE_ROUTING_KEY = "user.profile.routing.key";

    /**
     * 用户行为队列
     */
    @Bean
    public Queue userBehaviorQueue() {
        return QueueBuilder.durable(USER_BEHAVIOR_QUEUE)
                .withArgument("x-message-ttl", 86400000) // 消息TTL 24小时
                .build();
    }

    /**
     * 用户行为交换机
     */
    @Bean
    public TopicExchange userBehaviorExchange() {
        return new TopicExchange(USER_BEHAVIOR_EXCHANGE);
    }

    /**
     * 用户行为队列绑定
     */
    @Bean
    public Binding userBehaviorBinding() {
        return BindingBuilder
                .bind(userBehaviorQueue())
                .to(userBehaviorExchange())
                .with(USER_BEHAVIOR_ROUTING_KEY);
    }

    /**
     * 用户画像队列
     */
    @Bean
    public Queue userProfileQueue() {
        return QueueBuilder.durable(USER_PROFILE_QUEUE)
                .withArgument("x-message-ttl", 86400000) // 消息TTL 24小时
                .build();
    }

    /**
     * 用户画像交换机
     */
    @Bean
    public TopicExchange userProfileExchange() {
        return new TopicExchange(USER_PROFILE_EXCHANGE);
    }

    /**
     * 用户画像队列绑定
     */
    @Bean
    public Binding userProfileBinding() {
        return BindingBuilder
                .bind(userProfileQueue())
                .to(userProfileExchange())
                .with(USER_PROFILE_ROUTING_KEY);
    }

    /**
     * JSON消息转换器
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        
        // 设置发送确认
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                System.out.println("消息发送成功");
            } else {
                System.err.println("消息发送失败: " + cause);
            }
        });

        // 设置返回确认
        template.setReturnsCallback(returned -> {
            System.err.println("消息被退回: " + returned.getMessage());
        });

        return template;
    }

    /**
     * 监听器容器工厂配置
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setPrefetchCount(10);
        return factory;
    }
}