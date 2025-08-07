package com.recommendation.service.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.Pattern;
import java.util.Map;

/**
 * 推荐请求DTO
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
    @Min(value = 1, message = "推荐数量不能小于1")
    @Max(value = 100, message = "推荐数量不能大于100")
    private Integer size = 10;
    
    /**
     * 内容类型
     */
    @Pattern(regexp = "^(article|video|product|mixed)$", message = "内容类型只能是article、video、product或mixed")
    private String contentType = "mixed";
    
    /**
     * 场景标识
     */
    private String scene = "default";
    
    /**
     * 设备类型
     */
    private String deviceType;
    
    /**
     * 地理位置
     */
    private String location;
    
    /**
     * 扩展参数
     */
    private Map<String, Object> extraParams;
}