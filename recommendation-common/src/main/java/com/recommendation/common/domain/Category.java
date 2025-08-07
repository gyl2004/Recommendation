package com.recommendation.common.domain;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 分类领域模型
 * 支持需求2.1-2.4: 多类型内容管理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {
    
    /**
     * 分类ID
     */
    private Integer id;
    
    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50个字符")
    private String name;
    
    /**
     * 父分类ID，0表示顶级分类
     */
    @Builder.Default
    private Integer parentId = 0;
    
    /**
     * 分类层级
     */
    @Builder.Default
    private Integer level = 1;
    
    /**
     * 排序顺序
     */
    @Builder.Default
    private Integer sortOrder = 0;
    
    /**
     * 判断是否为顶级分类
     */
    public boolean isTopLevel() {
        return parentId == null || parentId == 0;
    }
}