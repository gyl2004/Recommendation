package com.recommendation.content.dto;

import com.recommendation.content.entity.ContentEntity;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 内容更新请求DTO
 */
@Data
public class ContentUpdateRequest {

    /**
     * 内容ID
     */
    @NotNull(message = "内容ID不能为空")
    private Long id;

    /**
     * 内容标题
     */
    @Size(max = 200, message = "内容标题长度不能超过200个字符")
    private String title;

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
     * 内容状态
     */
    private ContentEntity.ContentStatus status;
}