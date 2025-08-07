package com.recommendation.content.dto;

import com.recommendation.content.entity.ContentEntity;
import lombok.Data;

import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import java.util.List;

/**
 * 内容查询请求DTO
 */
@Data
public class ContentQueryRequest {

    /**
     * 内容类型
     */
    private ContentEntity.ContentType contentType;

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
    private ContentEntity.ContentStatus status = ContentEntity.ContentStatus.PUBLISHED;

    /**
     * 关键词搜索
     */
    private String keyword;

    /**
     * 标签过滤
     */
    private List<String> tags;

    /**
     * 排序字段
     */
    private String sortBy = "publishTime";

    /**
     * 排序方向
     */
    private String sortDirection = "desc";

    /**
     * 页码
     */
    @Min(value = 0, message = "页码不能小于0")
    private Integer page = 0;

    /**
     * 页大小
     */
    @Min(value = 1, message = "页大小不能小于1")
    @Max(value = 100, message = "页大小不能大于100")
    private Integer size = 20;
}