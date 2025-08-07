package com.recommendation.common.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 推荐反馈请求DTO
 * 支持需求7.1-7.4: 推荐效果监控
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
    @NotNull(message = "内容ID不能为空")
    private Long contentId;
    
    /**
     * 反馈类型
     */
    @NotBlank(message = "反馈类型不能为空")
    private String feedbackType;
    
    /**
     * 推荐请求追踪ID
     */
    private String traceId;
    
    /**
     * 反馈时间戳
     */
    private Long timestamp;
    
    /**
     * 额外反馈数据
     */
    private Map<String, Object> extraData;
    
    /**
     * 反馈类型枚举
     */
    public enum FeedbackType {
        CLICK("click", "点击"),
        LIKE("like", "点赞"),
        DISLIKE("dislike", "不喜欢"),
        SHARE("share", "分享"),
        REPORT("report", "举报");
        
        private final String code;
        private final String description;
        
        FeedbackType(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
}