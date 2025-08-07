package com.recommendation.content.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch内容文档模型
 * 对应设计文档中的索引映射结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentDocument {

    /**
     * 内容ID
     */
    private Long contentId;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容类型：article, video, product
     */
    private String contentType;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 分类
     */
    private String category;

    /**
     * 内容向量嵌入（用于相似度计算）
     */
    private List<Float> embedding;

    /**
     * 发布时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime publishTime;

    /**
     * 热度分数
     */
    private Float hotScore;

    /**
     * 作者ID
     */
    private Long authorId;

    /**
     * 内容摘要
     */
    private String summary;

    /**
     * 扩展数据
     */
    private Map<String, Object> extraData;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}