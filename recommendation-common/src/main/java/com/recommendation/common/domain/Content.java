package com.recommendation.common.domain;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 内容领域模型
 * 支持需求2.1-2.4: 多类型内容管理
 * 支持需求6.1-6.3: 特征工程处理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Content {
    
    /**
     * 内容ID
     */
    private Long id;
    
    /**
     * 内容标题
     */
    @NotBlank(message = "内容标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200个字符")
    private String title;
    
    /**
     * 内容类型
     */
    @NotNull(message = "内容类型不能为空")
    private ContentType contentType;
    
    /**
     * 内容数据 (JSON格式存储)
     * 根据内容类型存储不同的字段
     */
    private Map<String, Object> contentData;
    
    /**
     * 内容标签
     */
    private List<String> tags;
    
    /**
     * 分类ID
     */
    private Integer categoryId;
    
    /**
     * 作者ID
     */
    private Long authorId;
    
    /**
     * 内容状态
     */
    @Builder.Default
    private ContentStatus status = ContentStatus.DRAFT;
    
    /**
     * 发布时间
     */
    private LocalDateTime publishTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
    
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
     * 获取文章正文内容
     */
    public String getArticleContent() {
        if (contentType == ContentType.ARTICLE && contentData != null) {
            return (String) contentData.get("content");
        }
        return null;
    }
    
    /**
     * 获取视频时长
     */
    public Integer getVideoDuration() {
        if (contentType == ContentType.VIDEO && contentData != null) {
            Object duration = contentData.get("duration");
            if (duration instanceof Number) {
                return ((Number) duration).intValue();
            }
        }
        return null;
    }
    
    /**
     * 获取商品价格
     */
    public Double getProductPrice() {
        if (contentType == ContentType.PRODUCT && contentData != null) {
            Object price = contentData.get("price");
            if (price instanceof Number) {
                return ((Number) price).doubleValue();
            }
        }
        return null;
    }
}