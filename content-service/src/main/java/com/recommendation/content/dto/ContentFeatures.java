package com.recommendation.content.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 内容特征数据传输对象
 * 用于存储不同类型内容的特征数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentFeatures {

    /**
     * 内容ID
     */
    private Long contentId;

    /**
     * 内容类型
     */
    private String contentType;

    /**
     * 文本特征（用于文章和商品描述）
     */
    private TextFeatures textFeatures;

    /**
     * 视频特征
     */
    private VideoFeatures videoFeatures;

    /**
     * 商品特征
     */
    private ProductFeatures productFeatures;

    /**
     * 通用特征
     */
    private CommonFeatures commonFeatures;

    /**
     * 文本特征子类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextFeatures {
        /**
         * TF-IDF向量
         */
        private Map<String, Double> tfidfVector;

        /**
         * 词向量（简化版，实际应该是高维向量）
         */
        private List<Double> wordEmbedding;

        /**
         * 关键词列表
         */
        private List<String> keywords;

        /**
         * 文本长度
         */
        private Integer textLength;

        /**
         * 句子数量
         */
        private Integer sentenceCount;

        /**
         * 段落数量
         */
        private Integer paragraphCount;

        /**
         * 情感分数（-1到1，负数表示负面，正数表示正面）
         */
        private Double sentimentScore;
    }

    /**
     * 视频特征子类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VideoFeatures {
        /**
         * 视频时长（秒）
         */
        private Integer duration;

        /**
         * 视频分辨率宽度
         */
        private Integer width;

        /**
         * 视频分辨率高度
         */
        private Integer height;

        /**
         * 视频文件大小（字节）
         */
        private Long fileSize;

        /**
         * 视频格式
         */
        private String format;

        /**
         * 帧率
         */
        private Double frameRate;

        /**
         * 比特率
         */
        private Integer bitRate;

        /**
         * 是否有音频
         */
        private Boolean hasAudio;

        /**
         * 视频质量分数（0-1）
         */
        private Double qualityScore;

        /**
         * 时长分类（短视频、中视频、长视频）
         */
        private String durationCategory;
    }

    /**
     * 商品特征子类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductFeatures {
        /**
         * 价格
         */
        private Double price;

        /**
         * 价格区间（低、中、高）
         */
        private String priceRange;

        /**
         * 品牌
         */
        private String brand;

        /**
         * 商品属性特征向量
         */
        private Map<String, Object> attributeVector;

        /**
         * 图片数量
         */
        private Integer imageCount;

        /**
         * 描述长度
         */
        private Integer descriptionLength;

        /**
         * 商品评分
         */
        private Double rating;

        /**
         * 销量
         */
        private Integer salesCount;

        /**
         * 库存状态
         */
        private String stockStatus;

        /**
         * 商品新旧程度（新品、热销、清仓等）
         */
        private String productStatus;
    }

    /**
     * 通用特征子类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommonFeatures {
        /**
         * 标签列表
         */
        private List<String> tags;

        /**
         * 分类ID
         */
        private Integer categoryId;

        /**
         * 分类路径
         */
        private String categoryPath;

        /**
         * 热度分数
         */
        private Double hotScore;

        /**
         * 发布时间戳
         */
        private Long publishTimestamp;

        /**
         * 内容新鲜度（小时）
         */
        private Integer freshnessHours;

        /**
         * 作者ID
         */
        private Long authorId;

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
         * 互动率
         */
        private Double engagementRate;
    }
}