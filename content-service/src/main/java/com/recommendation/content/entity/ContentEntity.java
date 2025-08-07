package com.recommendation.content.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

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
 */
@Data
@Entity
@Table(name = "contents")
public class ContentEntity {

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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "内容标题不能为空")
    @Size(max = 200, message = "内容标题长度不能超过200个字符")
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @NotNull(message = "内容类型不能为空")
    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @NotNull(message = "内容数据不能为空")
    @Column(name = "content_data", nullable = false, columnDefinition = "JSON")
    private String contentData; // 简化为String，避免JSON类型依赖

    @Column(name = "tags", columnDefinition = "JSON")
    private String tags; // 简化为String

    @Column(name = "category_id")
    private Integer categoryId;

    @Column(name = "author_id")
    private Long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ContentStatus status = ContentStatus.DRAFT;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Column(name = "share_count", nullable = false)
    private Long shareCount = 0L;

    @Column(name = "comment_count", nullable = false)
    private Long commentCount = 0L;

    @Column(name = "hot_score", precision = 10, scale = 4, nullable = false)
    private BigDecimal hotScore = BigDecimal.ZERO;

    @Column(name = "publish_time")
    private LocalDateTime publishTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 检查内容是否已发布
     */
    public boolean isPublished() {
        return status == ContentStatus.PUBLISHED;
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