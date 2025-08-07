package com.recommendation.common.service;

import com.recommendation.common.domain.Content;
import com.recommendation.common.domain.Category;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内容服务接口
 * 支持需求2.1-2.4: 多类型内容管理
 * 支持需求6.1-6.3: 特征工程处理
 */
public interface ContentService {
    
    /**
     * 根据ID获取内容
     * 
     * @param contentId 内容ID
     * @return 内容信息
     */
    Optional<Content> getContentById(Long contentId);
    
    /**
     * 批量获取内容
     * 
     * @param contentIds 内容ID列表
     * @return 内容列表
     */
    List<Content> getContentsByIds(List<Long> contentIds);
    
    /**
     * 创建内容
     * 
     * @param content 内容信息
     * @return 创建的内容
     */
    Content createContent(Content content);
    
    /**
     * 更新内容
     * 
     * @param content 内容信息
     * @return 更新后的内容
     */
    Content updateContent(Content content);
    
    /**
     * 根据类型和分类获取内容列表
     * 
     * @param contentType 内容类型
     * @param categoryId 分类ID
     * @param page 页码
     * @param size 页大小
     * @return 内容列表
     */
    List<Content> getContentsByTypeAndCategory(Content.ContentType contentType, 
                                             Integer categoryId, 
                                             Integer page, 
                                             Integer size);
    
    /**
     * 搜索内容
     * 
     * @param keyword 关键词
     * @param contentType 内容类型
     * @param page 页码
     * @param size 页大小
     * @return 内容列表
     */
    List<Content> searchContents(String keyword, 
                               Content.ContentType contentType, 
                               Integer page, 
                               Integer size);
    
    /**
     * 获取内容特征
     * 
     * @param contentId 内容ID
     * @return 内容特征数据
     */
    Map<String, Object> getContentFeatures(Long contentId);
    
    /**
     * 获取热门内容
     * 
     * @param contentType 内容类型
     * @param size 数量
     * @return 热门内容列表
     */
    List<Content> getHotContents(Content.ContentType contentType, Integer size);
    
    /**
     * 获取所有分类
     * 
     * @return 分类列表
     */
    List<Category> getAllCategories();
    
    /**
     * 根据父分类获取子分类
     * 
     * @param parentId 父分类ID
     * @return 子分类列表
     */
    List<Category> getCategoriesByParent(Integer parentId);
}