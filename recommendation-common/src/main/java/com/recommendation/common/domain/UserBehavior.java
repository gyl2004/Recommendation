package com.recommendation.common.domain;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 用户行为领域模型
 * 支持需求1.1-1.4: 用户行为数据收集
 * 支持需求4.1-4.4: 用户画像构建
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehavior {
    
    /**
     * 行为ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    
    /**
     * 内容ID
     */
    @NotNull(message = "内容ID不能为空")
    private Long contentId;
    
    /**
     * 行为类型
     */
    @NotNull(message = "行为类型不能为空")
    private ActionType actionType;
    
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
    @NotNull(message = "行为时间不能为空")
    private LocalDateTime timestamp;
    
    /**
     * 行为持续时间(秒)
     */
    @Positive(message = "持续时间必须为正数")
    private Integer duration;
    
    /**
     * 额外数据 (JSON格式存储)
     * 如浏览深度、点击位置等
     */
    private Map<String, Object> extraData;
    
    /**
     * 行为类型枚举
     */
    public enum ActionType {
        VIEW("view", "浏览"),
        CLICK("click", "点击"),
        LIKE("like", "点赞"),
        SHARE("share", "分享"),
        COMMENT("comment", "评论"),
        COLLECT("collect", "收藏"),
        PURCHASE("purchase", "购买");
        
        private final String code;
        private final String description;
        
        ActionType(String code, String description) {
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
    
    /**
     * 获取浏览深度
     */
    public Double getViewDepth() {
        if (extraData != null && extraData.containsKey("view_depth")) {
            Object depth = extraData.get("view_depth");
            if (depth instanceof Number) {
                return ((Number) depth).doubleValue();
            }
        }
        return null;
    }
    
    /**
     * 获取点击位置
     */
    public String getClickPosition() {
        if (extraData != null && extraData.containsKey("click_position")) {
            return (String) extraData.get("click_position");
        }
        return null;
    }
    
    /**
     * 判断是否为正向行为
     */
    public boolean isPositiveAction() {
        return actionType == ActionType.LIKE || 
               actionType == ActionType.SHARE || 
               actionType == ActionType.COMMENT || 
               actionType == ActionType.COLLECT ||
               actionType == ActionType.PURCHASE;
    }
}