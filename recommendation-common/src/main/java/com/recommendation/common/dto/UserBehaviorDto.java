package com.recommendation.common.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户行为DTO
 * 支持需求1.1-1.4: 用户行为数据收集
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorDto {
    
    /**
     * 用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    /**
     * 内容ID
     */
    @NotNull(message = "内容ID不能为空")
    private Long contentId;
    
    /**
     * 行为类型
     */
    @NotBlank(message = "行为类型不能为空")
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
     * 行为发生时间
     */
    private LocalDateTime timestamp;
    
    /**
     * 行为持续时间(秒)
     */
    @Positive(message = "持续时间必须为正数")
    private Integer duration;
    
    /**
     * 额外数据
     */
    private Map<String, Object> extraData;
}