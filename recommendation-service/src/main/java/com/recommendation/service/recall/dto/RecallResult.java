package com.recommendation.service.recall.dto;

import com.recommendation.common.entity.Content;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 召回结果DTO
 * 支持需求5.1, 5.2: 推荐算法引擎 - 多路召回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallResult {
    
    /**
     * 召回的内容列表
     */
    private List<Content> contents;
    
    /**
     * 召回算法名称
     */
    private String algorithm;
    
    /**
     * 召回权重
     */
    private Double weight;
    
    /**
     * 召回数量
     */
    private Integer count;
    
    /**
     * 召回耗时(毫秒)
     */
    private Long duration;
    
    /**
     * 额外信息
     */
    private String metadata;
}