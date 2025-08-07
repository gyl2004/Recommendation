package com.recommendation.service.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Min;
import java.util.Map;

/**
 * 用户反馈请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {
    
    /**
     * 用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    /**
     * 内容ID
     */
    @NotBlank(message = "内容ID不能为空")
    private String contentId;
    
    /**
     * 反馈类型：click, like, share, comment, dislike, report
     */
    @Pattern(regexp = "^(click|like|share|comment|dislike|report)$", 
             message = "反馈类型只能是click、like、share、comment、dislike或report")
    private String feedbackType;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 请求ID，关联推荐请求
     */
    private String requestId;
    
    /**
     * 停留时长（毫秒）
     */
    @Min(value = 0, message = "停留时长不能为负数")
    private Long duration;
    
    /**
     * 位置信息（推荐列表中的位置）
     */
    private Integer position;
    
    /**
     * 设备类型
     */
    private String deviceType;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 扩展参数
     */
    private Map<String, Object> extraParams;
}