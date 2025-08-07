package com.recommendation.user.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 用户行为实体类
 */
@Data
@Entity
@Table(name = "user_behaviors", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_content_id", columnList = "content_id"),
    @Index(name = "idx_action_type", columnList = "action_type"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
public class UserBehaviorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "content_type", length = 50)
    private String contentType;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "extra_data", columnDefinition = "JSON")
    private String extraDataJson;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}