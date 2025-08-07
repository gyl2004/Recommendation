package com.recommendation.content.dto;

import com.recommendation.content.entity.ContentEntity;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 内容响应DTO
 */
@Data
@Builder
public class ContentResponse {

    /**
     * 内容ID
     */
    private Long id;

    /**
     * 内容标题
     */
    private String title;

    /**
     * 内容类型
     */
    private ContentEntity.ContentType contentType;

    /**
     * 内容数据
     */
    private Map<String, Object> contentData;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 分类ID
     */
    private Integer categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 作者名称
     */
    private String authorName;

    /**
     * 内容状态
     */
    private ContentEntity.ContentStatus status;

    /**
     * 浏览次数
     */
    private Long viewCount;

    /**
     * 点赞次数
     */
    private Long likeCount;

    /**
     * 分享次数
     */
    private Long shareCount;

    /**
     * 评论次数
     */
    private Long commentCount;

    /**
     * 热度分数
     */
    private BigDecimal hotScore;

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
     * 从实体转换为响应DTO
     */
    public static ContentResponse fromEntity(com.recommendation.content.entity.ContentEntity content, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        ContentResponseBuilder builder = ContentResponse.builder()
                .id(content.getId())
                .title(content.getTitle())
                .contentType(content.getContentType())
                .categoryId(content.getCategoryId())
                .authorId(content.getAuthorId())
                .status(content.getStatus())
                .viewCount(content.getViewCount())
                .likeCount(content.getLikeCount())
                .shareCount(content.getShareCount())
                .commentCount(content.getCommentCount())
                .hotScore(content.getHotScore())
                .publishTime(content.getPublishTime())
                .createdAt(content.getCreatedAt())
                .updatedAt(content.getUpdatedAt());

        // 解析JSON字符串为Map
        if (content.getContentData() != null) {
            try {
                Map<String, Object> contentData = objectMapper.readValue(content.getContentData(), Map.class);
                builder.contentData(contentData);
            } catch (Exception e) {
                // 如果解析失败，设置为空Map
                builder.contentData(new java.util.HashMap<>());
            }
        }

        // 解析JSON字符串为List
        if (content.getTags() != null) {
            try {
                List<String> tags = objectMapper.readValue(content.getTags(), List.class);
                builder.tags(tags);
            } catch (Exception e) {
                // 如果解析失败，设置为空List
                builder.tags(new java.util.ArrayList<>());
            }
        }

        return builder.build();
    }
}