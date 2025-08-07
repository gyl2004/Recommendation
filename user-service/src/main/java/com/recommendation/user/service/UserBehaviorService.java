package com.recommendation.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.user.entity.UserBehaviorEntity;
import com.recommendation.user.exception.UserServiceException;
import com.recommendation.user.repository.UserBehaviorEntityRepository;
import com.recommendation.user.dto.UserBehaviorRequest;
import com.recommendation.user.dto.UserBehaviorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户行为数据收集服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBehaviorService {

    private final UserBehaviorEntityRepository behaviorRepository;
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;

    // RabbitMQ队列名称
    private static final String USER_BEHAVIOR_QUEUE = "user.behavior.queue";
    private static final String USER_BEHAVIOR_EXCHANGE = "user.behavior.exchange";
    private static final String USER_BEHAVIOR_ROUTING_KEY = "user.behavior.routing.key";

    /**
     * 收集用户行为数据
     */
    @Transactional
    public UserBehaviorResponse collectBehavior(UserBehaviorRequest request) {
        log.info("收集用户行为数据: userId={}, contentId={}, actionType={}", 
                request.getUserId(), request.getContentId(), request.getActionType());

        try {
            // 创建行为实体
            UserBehaviorEntity behavior = new UserBehaviorEntity();
            behavior.setUserId(request.getUserId());
            behavior.setContentId(request.getContentId());
            behavior.setActionType(request.getActionType());
            behavior.setContentType(request.getContentType());
            behavior.setSessionId(request.getSessionId());
            behavior.setDeviceType(request.getDeviceType());
            behavior.setDuration(request.getDuration());

            // 序列化额外数据
            if (request.getExtraData() != null) {
                String extraDataJson = objectMapper.writeValueAsString(request.getExtraData());
                behavior.setExtraDataJson(extraDataJson);
            }

            // 保存到数据库
            UserBehaviorEntity savedBehavior = behaviorRepository.save(behavior);
            log.info("用户行为数据保存成功: {}", savedBehavior.getId());

            // 异步发送到消息队列进行后续处理
            sendBehaviorToQueue(savedBehavior);

            return convertToBehaviorResponse(savedBehavior);

        } catch (JsonProcessingException e) {
            log.error("用户行为数据序列化失败", e);
            throw new UserServiceException("用户行为数据序列化失败", e);
        } catch (Exception e) {
            log.error("收集用户行为数据失败", e);
            throw new UserServiceException("收集用户行为数据失败", e);
        }
    }

    /**
     * 批量收集用户行为数据
     */
    @Transactional
    public List<UserBehaviorResponse> collectBehaviors(List<UserBehaviorRequest> requests) {
        log.info("批量收集用户行为数据: {} 条记录", requests.size());

        return requests.stream()
                .map(this::collectBehavior)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户行为历史
     */
    public List<UserBehaviorResponse> getUserBehaviorHistory(Long userId, int limit) {
        log.info("获取用户行为历史: userId={}, limit={}", userId, limit);

        List<UserBehaviorEntity> behaviors = behaviorRepository.findRecentBehaviorsByUserId(userId);
        
        return behaviors.stream()
                .limit(limit)
                .map(this::convertToBehaviorResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取用户在指定时间范围内的行为数据
     */
    public List<UserBehaviorResponse> getUserBehaviorsByTimeRange(Long userId, 
                                                                 LocalDateTime startTime, 
                                                                 LocalDateTime endTime) {
        log.info("获取用户时间范围内行为数据: userId={}, startTime={}, endTime={}", 
                userId, startTime, endTime);

        List<UserBehaviorEntity> behaviors = behaviorRepository
                .findByUserIdAndTimestampBetween(userId, startTime, endTime);

        return behaviors.stream()
                .map(this::convertToBehaviorResponse)
                .collect(Collectors.toList());
    }

    /**
     * 获取内容的行为统计
     */
    public List<UserBehaviorResponse> getContentBehaviors(Long contentId) {
        log.info("获取内容行为统计: contentId={}", contentId);

        List<UserBehaviorEntity> behaviors = behaviorRepository.findByContentId(contentId);

        return behaviors.stream()
                .map(this::convertToBehaviorResponse)
                .collect(Collectors.toList());
    }

    /**
     * 统计用户活跃度
     */
    public long getUserActivityCount(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        return behaviorRepository.countByUserIdAndTimestampBetween(userId, startTime, endTime);
    }

    /**
     * 获取热门内容
     */
    public List<Object[]> getPopularContent(LocalDateTime since) {
        return behaviorRepository.findPopularContentSince(since);
    }

    /**
     * 发送行为数据到消息队列
     */
    private void sendBehaviorToQueue(UserBehaviorEntity behavior) {
        try {
            // 创建消息对象
            Map<String, Object> message = Map.of(
                "behaviorId", behavior.getId(),
                "userId", behavior.getUserId(),
                "contentId", behavior.getContentId(),
                "actionType", behavior.getActionType(),
                "contentType", behavior.getContentType() != null ? behavior.getContentType() : "",
                "timestamp", behavior.getTimestamp().toString(),
                "duration", behavior.getDuration() != null ? behavior.getDuration() : 0
            );

            // 发送到RabbitMQ
            rabbitTemplate.convertAndSend(USER_BEHAVIOR_EXCHANGE, USER_BEHAVIOR_ROUTING_KEY, message);
            log.debug("用户行为数据已发送到消息队列: behaviorId={}", behavior.getId());

        } catch (Exception e) {
            log.error("发送用户行为数据到消息队列失败: behaviorId={}", behavior.getId(), e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 转换实体为响应DTO
     */
    private UserBehaviorResponse convertToBehaviorResponse(UserBehaviorEntity behavior) {
        Map<String, Object> extraData = null;
        if (behavior.getExtraDataJson() != null) {
            try {
                extraData = objectMapper.readValue(behavior.getExtraDataJson(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("用户行为额外数据反序列化失败: behaviorId={}", behavior.getId(), e);
            }
        }

        return UserBehaviorResponse.builder()
                .id(behavior.getId())
                .userId(behavior.getUserId())
                .contentId(behavior.getContentId())
                .actionType(behavior.getActionType())
                .contentType(behavior.getContentType())
                .sessionId(behavior.getSessionId())
                .deviceType(behavior.getDeviceType())
                .timestamp(behavior.getTimestamp())
                .duration(behavior.getDuration())
                .extraData(extraData)
                .build();
    }
}