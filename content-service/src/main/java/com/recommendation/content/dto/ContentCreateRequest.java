package com.recommendation.content.dto;

import com.recommendation.content.entity.ContentEntity;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 内容创建请求DTO
 */
@Data
public class ContentCreateRequest {

    /**
     * 内容标题
     */
    @NotBlank(message = "内容标题不能为空")
    @Size(max = 200, message = "内容标题长度不能超过200个字符")
    private String title;

    /**
     * 内容类型
     */
    @NotNull(message = "内容类型不能为空")
    private ContentEntity.ContentType contentType;

    /**
     * 内容数据
     */
    @NotNull(message = "内容数据不能为空")
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
     * 作者ID
     */
    private Long authorId;

    /**
     * 内容状态
     */
    private ContentEntity.ContentStatus status = ContentEntity.ContentStatus.DRAFT;
}