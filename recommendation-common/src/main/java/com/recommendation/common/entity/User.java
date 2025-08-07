package com.recommendation.common.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 用户实体类
 * 对应数据库users表
 */
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"contents", "behaviors", "recommendationLogs"})
@ToString(exclude = {"contents", "behaviors", "recommendationLogs"})
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username"),
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_phone", columnList = "phone"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class User extends BaseEntity {

    /**
     * 用户ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50个字符之间")
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    @Column(name = "email", unique = true, length = 100)
    private String email;

    /**
     * 手机号
     */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * 用户画像数据(JSON格式)
     * 包含年龄、性别、兴趣标签、行为偏好等信息
     */
    @Type(type = "json")
    @Column(name = "profile_data", columnDefinition = "JSON")
    private Map<String, Object> profileData;

    /**
     * 用户状态
     * 1-正常, 0-禁用
     */
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    /**
     * 用户创建的内容
     */
    @JsonIgnore
    @OneToMany(mappedBy = "author", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Content> contents;

    /**
     * 用户行为记录
     */
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<UserBehavior> behaviors;

    /**
     * 推荐记录
     */
    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RecommendationLog> recommendationLogs;

    /**
     * 检查用户是否激活
     */
    public boolean isActive() {
        return status != null && status == 1;
    }

    /**
     * 获取用户画像中的特定属性
     */
    public Object getProfileAttribute(String key) {
        return profileData != null ? profileData.get(key) : null;
    }

    /**
     * 设置用户画像中的特定属性
     */
    public void setProfileAttribute(String key, Object value) {
        if (profileData == null) {
            profileData = new java.util.HashMap<>();
        }
        profileData.put(key, value);
    }
}