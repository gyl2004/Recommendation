package com.recommendation.common.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Max;
import java.util.Map;

/**
 * 推荐请求DTO
 * 支持需求3.1-3.4: 实时推荐服务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendRequest {
    
    /**
     * 用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;
    
    /**
     * 推荐内容数量
     */
    @Positive(message = "推荐数量必须为正数")
    @Max(value = 100, message = "推荐数量不能超过100")
    @Builder.Default
    private Integer size = 10;
    
    /**
     * 内容类型过滤
     * mixed: 混合类型, article: 文章, video: 视频, product: 商品
     */
    @Builder.Default
    private String contentType = "mixed";
    
    /**
     * 分类ID过滤
     */
    private Integer categoryId;
    
    /**
     * 上下文信息
     * 如设备类型、地理位置、时间等
     */
    private Map<String, Object> context;
    
    /**
     * 获取设备类型
     */
    public String getDeviceType() {
        if (context != null && context.containsKey("device_type")) {
            return (String) context.get("device_type");
        }
        return "unknown";
    }
    
    /**
     * 获取地理位置
     */
    public String getLocation() {
        if (context != null && context.containsKey("location")) {
            return (String) context.get("location");
        }
        return null;
    }
}