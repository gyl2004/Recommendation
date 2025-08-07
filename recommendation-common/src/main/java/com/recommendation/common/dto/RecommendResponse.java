package com.recommendation.common.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 推荐响应DTO
 * 支持需求3.1-3.4: 实时推荐服务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResponse {
    
    /**
     * 推荐内容列表
     */
    private List<RecommendItem> items;
    
    /**
     * 推荐算法信息
     */
    private String algorithm;
    
    /**
     * 推荐时间戳
     */
    private Long timestamp;
    
    /**
     * 请求追踪ID
     */
    private String traceId;
    
    /**
     * 额外元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 推荐项DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendItem {
        
        /**
         * 内容ID
         */
        private Long contentId;
        
        /**
         * 内容标题
         */
        private String title;
        
        /**
         * 内容类型
         */
        private String contentType;
        
        /**
         * 推荐分数
         */
        private Double score;
        
        /**
         * 推荐原因
         */
        private String reason;
        
        /**
         * 内容摘要信息
         */
        private Map<String, Object> summary;
        
        /**
         * 获取内容摘要
         */
        public String getContentSummary() {
            if (summary != null && summary.containsKey("summary")) {
                return (String) summary.get("summary");
            }
            return null;
        }
        
        /**
         * 获取内容封面图
         */
        public String getCoverImage() {
            if (summary != null && summary.containsKey("cover_image")) {
                return (String) summary.get("cover_image");
            }
            return null;
        }
    }
}