package com.recommendation.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * 用户行为实体类
 * 对应数据库user_behaviors表
 */
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"user", "content"})
@ToString(exclude = {"user", "content"})
@Entity
@Table(name = "user_behaviors", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_content_id", columnList = "content_id"),
    @Index(name = "idx_action_type", columnList = "action_type"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_user_content", columnList = "user_id, content_id"),
    @Index(name = "idx_user_action_time", columnList = "user_id, action_type, created_at")
})
public class UserBehavior extends BaseEntity {

    /**
     * 行为类型枚举
     */
    public enum ActionType {
        VIEW("view", "浏览"),
        CLICK("click", "点击"),
        LIKE("like", "点赞"),
        SHARE("share", "分享"),
        COMMENT("comment", "评论"),
        COLLECT("collect", "收藏");

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
     * 行为ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 内容ID
     */
    @NotNull(message = "内容ID不能为空")
    @Column(name = "content_id", nullable = false)
    private Long contentId;

    /**
     * 行为类型
     */
    @NotNull(message = "行为类型不能为空")
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    /**
     * 会话ID
     */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /**
     * 设备类型
     */
    @Column(name = "device_type", length = 20)
    private String deviceType;

    /**
     * 停留时长(秒)
     */
    @Column(name = "duration", nullable = false)
    private Integer duration = 0;

    /**
     * 额外数据(JSON格式)
     * 可以存储位置信息、推荐算法类型、页面来源等
     */
    @Type(type = "json")
    @Column(name = "extra_data", columnDefinition = "JSON")
    private Map<String, Object> extraData;

    /**
     * 用户信息
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /**
     * 内容信息
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", insertable = false, updatable = false)
    private Content content;

    /**
     * 获取额外数据中的特定属性
     */
    public Object getExtraAttribute(String key) {
        return extraData != null ? extraData.get(key) : null;
    }

    /**
     * 设置额外数据中的特定属性
     */
    public void setExtraAttribute(String key, Object value) {
        if (extraData == null) {
            extraData = new java.util.HashMap<>();
        }
        extraData.put(key, value);
    }

    /**
     * 检查是否为正向行为(点赞、分享、收藏等)
     */
    public boolean isPositiveAction() {
        return actionType == ActionType.LIKE || 
               actionType == ActionType.SHARE || 
               actionType == ActionType.COLLECT ||
               actionType == ActionType.COMMENT;
    }

    /**
     * 获取行为权重(用于计算用户偏好)
     */
    public double getActionWeight() {
        switch (actionType) {
            case VIEW:
                return 1.0;
            case CLICK:
                return 2.0;
            case LIKE:
                return 3.0;
            case SHARE:
                return 5.0;
            case COMMENT:
                return 4.0;
            case COLLECT:
                return 6.0;
            default:
                return 1.0;
        }
    }
}