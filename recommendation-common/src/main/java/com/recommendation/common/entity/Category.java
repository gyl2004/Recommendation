package com.recommendation.common.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 分类实体类
 * 对应数据库categories表
 */
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"contents", "children", "parent"})
@ToString(exclude = {"contents", "children", "parent"})
@Entity
@Table(name = "categories", indexes = {
    @Index(name = "idx_parent_id", columnList = "parent_id"),
    @Index(name = "idx_level", columnList = "level"),
    @Index(name = "idx_sort_order", columnList = "sort_order"),
    @Index(name = "idx_status", columnList = "status")
})
public class Category extends BaseEntity {

    /**
     * 分类ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50个字符")
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /**
     * 父分类ID，0表示顶级分类
     */
    @Column(name = "parent_id", nullable = false)
    private Integer parentId = 0;

    /**
     * 分类层级
     */
    @Column(name = "level", nullable = false)
    private Integer level = 1;

    /**
     * 排序权重
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 分类描述
     */
    @Size(max = 200, message = "分类描述长度不能超过200个字符")
    @Column(name = "description", length = 200)
    private String description;

    /**
     * 状态: 1-启用, 0-禁用
     */
    @Column(name = "status", nullable = false)
    private Integer status = 1;

    /**
     * 父分类
     */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private Category parent;

    /**
     * 子分类列表
     */
    @JsonIgnore
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC, id ASC")
    private List<Category> children;

    /**
     * 该分类下的内容
     */
    @JsonIgnore
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Content> contents;

    /**
     * 检查分类是否启用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }

    /**
     * 检查是否为顶级分类
     */
    public boolean isTopLevel() {
        return parentId != null && parentId == 0;
    }

    /**
     * 获取分类的完整路径名称
     */
    public String getFullPath() {
        if (parent != null && !parent.isTopLevel()) {
            return parent.getFullPath() + " > " + name;
        }
        return name;
    }
}