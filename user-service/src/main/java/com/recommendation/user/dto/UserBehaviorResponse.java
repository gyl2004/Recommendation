package com.recommendation.user.dto;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户行为响应DTO
 */
@Data
@Builder
public class UserBehaviorResponse {

    /**
     * 行为ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 内容ID
     */
    private Long contentId;

    /**
     * 行为类型
     */
    private String actionType;

    /**
     * 内容类型
     */
    private String contentType;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 行为时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 行为持续时间（秒）
     */
    private Integer duration;

    /**
     * 额外数据
     */
    private Map<String, Object> extraData;
}