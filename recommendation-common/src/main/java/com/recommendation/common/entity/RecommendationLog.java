package com.recommendation.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 推荐记录实体类
 * 对应数据库recommendation_logs表
 */
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"user", "content"})
@ToString(exclude = {"user", "content"})
@Entity
@Table(name = "recommendation_logs", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_algorithm_type", columnList = "algorithm_type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class RecommendationLog extends BaseEntity {

    /**
     * 推荐记录ID
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
     * 推荐的内容ID列表(JSON格式)
     */
    @NotNull(message = "推荐内容ID列表不能为空")
    @Type(type = "json")
    @Column(name = "content_ids", nullable = false, columnDefinition = "JSON")
    private List<Long> contentIds;

    /**
     * 推荐算法类型
     */
    @NotNull(message = "推荐算法类型不能为空")
    @Column(name = "algorithm_type", nullable = false, length = 50)
    private String algorithmType;

    /**
     * 请求参数(JSON格式)
     */
    @Type(type = "json")
    @Column(name = "request_params", columnDefinition = "JSON")
    private Map<String, Object> requestParams;

    /**
     * 响应时间(毫秒)
     */
    @Column(name = "response_time")
    private Integer responseTime;

    /**
     * 用户信息
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /**
     * 内容信息(仅用于关联查询，实际存储在contentIds中)
     */
    @Transient
    private Content content;

    /**
     * 获取请求参数中的特定属性
     */
    public Object getRequestParam(String key) {
        return requestParams != null ? requestParams.get(key) : null;
    }

    /**
     * 设置请求参数中的特定属性
     */
    public void setRequestParam(String key, Object value) {
        if (requestParams == null) {
            requestParams = new java.util.HashMap<>();
        }
        requestParams.put(key, value);
    }

    /**
     * 获取推荐内容数量
     */
    public int getRecommendationCount() {
        return contentIds != null ? contentIds.size() : 0;
    }

    /**
     * 检查是否包含指定内容
     */
    public boolean containsContent(Long contentId) {
        return contentIds != null && contentIds.contains(contentId);
    }

    /**
     * 添加推荐内容ID
     */
    public void addContentId(Long contentId) {
        if (contentIds == null) {
            contentIds = new java.util.ArrayList<>();
        }
        if (!contentIds.contains(contentId)) {
            contentIds.add(contentId);
        }
    }

    /**
     * 移除推荐内容ID
     */
    public void removeContentId(Long contentId) {
        if (contentIds != null) {
            contentIds.remove(contentId);
        }
    }
}