package com.recommendation.common.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 内容实体类
 * 对应数据库contents表
 */
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"author", "category", "behaviors", "recommendationLogs"})
@ToString(exclude = {"author", "category", "behaviors", "recommendationLogs"})
@Entity
@Table(name = "contents", indexes = {
    @Index(name = "idx_type_status", columnList = "content_type, status"),
    @Index(name = "idx_category", columnList = "category_id"),
    @Index(name = "idx_author", columnList = "author_id"),
    @Index(name = "idx_publish_time", columnList = "publish_time"),
    @Index(name = "idx_hot_score", columnList = "hot_score"),
    @Index(name = "idx_view_count", columnList = "view_count")
})
public class Content extends BaseEntity {

    /**
     * 内容类型枚举
     */
    public enum ContentType {
        ARTICLE("article", "文章"),
        VIDEO("video", "视频"),
        PRODUCT("product", "商品");

        private final String code;
        private final String description;

        ContentType(String code, String description) {
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
     * 内容状态枚举
     */
    public enum ContentStatus {
        DRAFT("draft", "草稿"),
        PUBLISHED("published", "已发布"),
        ARCHIVED("archived", "已归档");

        private final String code;
        private final String description;

        ContentStatus(String code, String description) {
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
     * 内容ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 内容标题
     */
    @NotBlank(message = "内容标题不能为空")
    @Size(max = 200, message = "内容标题长度不能超过200个字符")
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 内容类型
     */
    @NotNull(message = "内容类型不能为空")
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    /**
     * 内容数据(JSON格式)
     * 根据内容类型存储不同的字段
     */
    @NotNull(message = "内容数据不能为空")
    @Type(type = "json")
    @Column(name = "content_data", nullable = false, columnDefinition = "JSON")
    private Map<String, Object> contentData;

    /**
     * 标签列表(JSON格式)
     */
    @Type(type = "json")
    @Column(name = "tags", columnDefinition = "JSON")
    private List<String> tags;

    /**
     * 分类ID
     */
    @Column(name = "category_id")
    private Integer categoryId;

    /**
     * 作者ID
     */
    @Column(name = "author_id")
    private Long authorId;

    /**
     * 内容状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContentStatus status = ContentStatus.DRAFT;

    /**
     * 浏览次数
     */
    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    /**
     * 点赞次数
     */
    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    /**
     * 分享次数
     */
    @Column(name = "share_count", nullable = false)
    private Long shareCount = 0L;

    /**
     * 评论次数
     */
    @Column(name = "comment_count", nullable = false)
    private Long commentCount = 0L;

    /**
     * 热度分数
     */
    @Column(name = "hot_score", precision = 10, scale = 4, nullable = false)
    private BigDecimal hotScore = BigDecimal.ZERO;

    /**
     * 发布时间
     */
    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    /**
     * 作者信息
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", insertable = false, updatable = false)
    private User author;

    /**
     * 分类信息
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private Category category;

    /**
     * 用户行为记录
     */
    @JsonIgnore
    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserBehavior> behaviors;

    /**
     * 推荐记录
     */
    @JsonIgnore
    @OneToMany(mappedBy = "content", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RecommendationLog> recommendationLogs;

    /**
     * 检查内容是否已发布
     */
    public boolean isPublished() {
        return status == ContentStatus.PUBLISHED;
    }

    /**
     * 获取内容数据中的特定属性
     */
    public Object getContentAttribute(String key) {
        return contentData != null ? contentData.get(key) : null;
    }

    /**
     * 设置内容数据中的特定属性
     */
    public void setContentAttribute(String key, Object value) {
        if (contentData == null) {
            contentData = new java.util.HashMap<>();
        }
        contentData.put(key, value);
    }

    /**
     * 增加浏览次数
     */
    public void incrementViewCount() {
        this.viewCount = (this.viewCount == null ? 0L : this.viewCount) + 1;
    }

    /**
     * 增加点赞次数
     */
    public void incrementLikeCount() {
        this.likeCount = (this.likeCount == null ? 0L : this.likeCount) + 1;
    }

    /**
     * 增加分享次数
     */
    public void incrementShareCount() {
        this.shareCount = (this.shareCount == null ? 0L : this.shareCount) + 1;
    }

    /**
     * 增加评论次数
     */
    public void incrementCommentCount() {
        this.commentCount = (this.commentCount == null ? 0L : this.commentCount) + 1;
    }

    /**
     * 计算并更新热度分数
     * 基于浏览、点赞、分享、评论等指标
     */
    public void updateHotScore() {
        double score = 0.0;
        if (viewCount != null) score += viewCount * 0.1;
        if (likeCount != null) score += likeCount * 2.0;
        if (shareCount != null) score += shareCount * 5.0;
        if (commentCount != null) score += commentCount * 3.0;
        
        // 时间衰减因子
        if (publishTime != null) {
            long daysSincePublish = java.time.Duration.between(publishTime, LocalDateTime.now()).toDays();
            double timeDecay = Math.exp(-daysSincePublish * 0.1);
            score *= timeDecay;
        }
        
        this.hotScore = BigDecimal.valueOf(score).setScale(4, BigDecimal.ROUND_HALF_UP);
    }
}