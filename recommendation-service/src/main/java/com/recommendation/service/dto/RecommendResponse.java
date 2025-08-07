package com.recommendation.service.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 推荐响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResponse {
    
    /**
     * 推荐结果列表
     */
    private List<RecommendItem> items;
    
    /**
     * 总数量
     */
    private Integer total;
    
    /**
     * 请求ID，用于追踪
     */
    private String requestId;
    
    /**
     * 算法版本
     */
    private String algorithmVersion;
    
    /**
     * 是否来自缓存
     */
    private Boolean fromCache;
    
    /**
     * 扩展信息
     */
    private Map<String, Object> extraInfo;
    
    /**
     * 推荐项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendItem {
        
        /**
         * 内容ID
         */
        private String contentId;
        
        /**
         * 内容类型
         */
        private String contentType;
        
        /**
         * 标题
         */
        private String title;
        
        /**
         * 描述
         */
        private String description;
        
        /**
         * 封面图片URL
         */
        private String coverUrl;
        
        /**
         * 作者信息
         */
        private String author;
        
        /**
         * 发布时间
         */
        private Long publishTime;
        
        /**
         * 推荐分数
         */
        private Double score;
        
        /**
         * 推荐理由
         */
        private String reason;
        
        /**
         * 置信度
         */
        private Double confidence;
        
        /**
         * 标签
         */
        private List<String> tags;
        
        /**
         * 扩展数据
         */
        private Map<String, Object> extraData;
    }
}