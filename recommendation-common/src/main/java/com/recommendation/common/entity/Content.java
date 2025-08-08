package com.recommendation.common.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "contents")
public class Content extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "content_type")
    private String contentType;
    
    @Column(name = "category_id")
    private Long categoryId;
    
    @Column(name = "author_id")
    private Long authorId;
    
    private String tags;
    private String status;
    
    @Column(name = "publish_time")
    private LocalDateTime publishTime;
    
    @Column(name = "view_count")
    private Integer viewCount = 0;
    
    @Column(name = "like_count")
    private Integer likeCount = 0;
    
    @Column(name = "share_count")
    private Integer shareCount = 0;
    
    @Column(name = "comment_count")
    private Integer commentCount = 0;
    
    @Column(name = "hot_score")
    private Double hotScore = 0.0;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
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
}
