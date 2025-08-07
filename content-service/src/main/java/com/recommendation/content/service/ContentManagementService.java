package com.recommendation.content.service;

import com.recommendation.content.entity.ContentEntity;
import com.recommendation.content.repository.ContentEntityRepository;
import com.recommendation.content.dto.*;
import com.recommendation.content.exception.ContentException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 内容管理服务实现
 * 实现需求2.1-2.4: 多类型内容管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentManagementService {

    private final ContentEntityRepository contentRepository;
    private final ObjectMapper objectMapper;
    private final ContentSearchService contentSearchService;

    /**
     * 创建内容
     */
    @Transactional
    public ContentResponse createContent(ContentCreateRequest request) {
        log.info("创建内容: {}", request.getTitle());

        // 验证内容数据格式
        validateContentData(request.getContentType(), request.getContentData());

        // 创建内容实体
        ContentEntity content = new ContentEntity();
        content.setTitle(request.getTitle());
        content.setContentType(request.getContentType());
        
        // 将Map转换为JSON字符串
        try {
            content.setContentData(objectMapper.writeValueAsString(request.getContentData()));
        } catch (JsonProcessingException e) {
            throw new ContentException("内容数据序列化失败", e);
        }
        
        // 将标签列表转换为JSON字符串
        if (request.getTags() != null) {
            try {
                content.setTags(objectMapper.writeValueAsString(request.getTags()));
            } catch (JsonProcessingException e) {
                throw new ContentException("标签数据序列化失败", e);
            }
        }
        
        content.setCategoryId(request.getCategoryId());
        content.setAuthorId(request.getAuthorId());
        content.setStatus(request.getStatus());

        // 如果状态为已发布，设置发布时间
        if (request.getStatus() == ContentEntity.ContentStatus.PUBLISHED) {
            content.setPublishTime(LocalDateTime.now());
        }

        // 保存内容
        ContentEntity savedContent = contentRepository.save(content);
        log.info("内容创建成功，ID: {}", savedContent.getId());

        // 如果内容已发布，自动索引到Elasticsearch
        if (savedContent.getStatus() == ContentEntity.ContentStatus.PUBLISHED) {
            try {
                contentSearchService.indexContent(savedContent);
            } catch (Exception e) {
                log.error("Failed to index content to Elasticsearch: {}", savedContent.getId(), e);
                // 不影响主流程，只记录错误
            }
        }

        return ContentResponse.fromEntity(savedContent, objectMapper);
    }

    /**
     * 更新内容
     */
    @Transactional
    public ContentResponse updateContent(ContentUpdateRequest request) {
        log.info("更新内容: {}", request.getId());

        // 查找现有内容
        ContentEntity existingContent = contentRepository.findById(request.getId())
                .orElseThrow(() -> new ContentException("内容不存在: " + request.getId()));

        // 更新字段
        if (StringUtils.hasText(request.getTitle())) {
            existingContent.setTitle(request.getTitle());
        }

        if (request.getContentData() != null) {
            validateContentData(existingContent.getContentType(), request.getContentData());
            try {
                existingContent.setContentData(objectMapper.writeValueAsString(request.getContentData()));
            } catch (JsonProcessingException e) {
                throw new ContentException("内容数据序列化失败", e);
            }
        }

        if (request.getTags() != null) {
            try {
                existingContent.setTags(objectMapper.writeValueAsString(request.getTags()));
            } catch (JsonProcessingException e) {
                throw new ContentException("标签数据序列化失败", e);
            }
        }

        if (request.getCategoryId() != null) {
            existingContent.setCategoryId(request.getCategoryId());
        }

        if (request.getStatus() != null) {
            // 如果从非发布状态改为发布状态，设置发布时间
            if (existingContent.getStatus() != ContentEntity.ContentStatus.PUBLISHED 
                && request.getStatus() == ContentEntity.ContentStatus.PUBLISHED) {
                existingContent.setPublishTime(LocalDateTime.now());
            }
            existingContent.setStatus(request.getStatus());
        }

        // 保存更新
        ContentEntity updatedContent = contentRepository.save(existingContent);
        log.info("内容更新成功，ID: {}", updatedContent.getId());

        // 更新Elasticsearch索引
        if (updatedContent.getStatus() == ContentEntity.ContentStatus.PUBLISHED) {
            try {
                contentSearchService.updateContentIndex(updatedContent);
            } catch (Exception e) {
                log.error("Failed to update content index in Elasticsearch: {}", updatedContent.getId(), e);
                // 不影响主流程，只记录错误
            }
        } else {
            // 如果内容不再是发布状态，从索引中删除
            try {
                contentSearchService.deleteContentIndex(updatedContent.getId());
            } catch (Exception e) {
                log.error("Failed to delete content index from Elasticsearch: {}", updatedContent.getId(), e);
                // 不影响主流程，只记录错误
            }
        }

        return ContentResponse.fromEntity(updatedContent, objectMapper);
    }

    /**
     * 根据ID获取内容
     */
    @Transactional(readOnly = true)
    public ContentResponse getContentById(Long contentId) {
        log.debug("获取内容详情: {}", contentId);

        ContentEntity content = contentRepository.findById(contentId)
                .orElseThrow(() -> new ContentException("内容不存在: " + contentId));

        return ContentResponse.fromEntity(content, objectMapper);
    }

    /**
     * 查询内容列表
     */
    @Transactional(readOnly = true)
    public PageResponse<ContentResponse> queryContents(ContentQueryRequest request) {
        log.debug("查询内容列表: {}", request);

        // 构建分页和排序
        Sort sort = buildSort(request.getSortBy(), request.getSortDirection());
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);

        Page<ContentEntity> contentPage;

        // 根据查询条件执行不同的查询
        if (StringUtils.hasText(request.getKeyword())) {
            // 关键词搜索
            contentPage = contentRepository.findByTitleContainingIgnoreCaseAndStatus(
                    request.getKeyword(), request.getStatus(), pageable);
        } else if (request.getContentType() != null && request.getCategoryId() != null) {
            // 按类型和分类查询
            contentPage = contentRepository.findByContentTypeAndStatus(
                    request.getContentType(), request.getStatus(), pageable);
        } else if (request.getContentType() != null) {
            // 按类型查询
            contentPage = contentRepository.findByContentTypeAndStatus(
                    request.getContentType(), request.getStatus(), pageable);
        } else if (request.getCategoryId() != null) {
            // 按分类查询
            contentPage = contentRepository.findByCategoryIdAndStatus(
                    request.getCategoryId(), request.getStatus(), pageable);
        } else if (request.getAuthorId() != null) {
            // 按作者查询
            contentPage = contentRepository.findByAuthorIdAndStatus(
                    request.getAuthorId(), request.getStatus(), pageable);
        } else {
            // 查询所有
            contentPage = contentRepository.findByStatus(request.getStatus(), pageable);
        }

        // 转换为响应DTO
        Page<ContentResponse> responsePage = contentPage.map(content -> ContentResponse.fromEntity(content, objectMapper));
        return PageResponse.fromPage(responsePage);
    }

    /**
     * 删除内容
     */
    @Transactional
    public void deleteContent(Long contentId) {
        log.info("删除内容: {}", contentId);

        if (!contentRepository.existsById(contentId)) {
            throw new ContentException("内容不存在: " + contentId);
        }

        contentRepository.deleteById(contentId);
        log.info("内容删除成功: {}", contentId);

        // 从Elasticsearch索引中删除
        try {
            contentSearchService.deleteContentIndex(contentId);
        } catch (Exception e) {
            log.error("Failed to delete content index from Elasticsearch: {}", contentId, e);
            // 不影响主流程，只记录错误
        }
    }

    /**
     * 批量更新内容状态
     */
    @Transactional
    public void batchUpdateStatus(List<Long> contentIds, ContentEntity.ContentStatus status) {
        log.info("批量更新内容状态: {} -> {}", contentIds, status);

        int updatedCount = contentRepository.updateStatusByIds(contentIds, status);
        log.info("批量更新完成，影响行数: {}", updatedCount);
    }

    /**
     * 获取热门内容
     */
    @Transactional(readOnly = true)
    public PageResponse<ContentResponse> getHotContents(ContentEntity.ContentType contentType, Integer page, Integer size) {
        log.debug("获取热门内容: type={}, page={}, size={}", contentType, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<ContentEntity> contentPage;

        if (contentType != null) {
            // 按类型获取热门内容
            contentPage = contentRepository.findByContentTypeAndStatus(
                    contentType, ContentEntity.ContentStatus.PUBLISHED, pageable);
        } else {
            // 获取所有热门内容
            contentPage = contentRepository.findHotContents(pageable);
        }

        Page<ContentResponse> responsePage = contentPage.map(content -> ContentResponse.fromEntity(content, objectMapper));
        return PageResponse.fromPage(responsePage);
    }

    /**
     * 获取最新内容
     */
    @Transactional(readOnly = true)
    public PageResponse<ContentResponse> getLatestContents(ContentEntity.ContentType contentType, Integer page, Integer size) {
        log.debug("获取最新内容: type={}, page={}, size={}", contentType, page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<ContentEntity> contentPage = contentRepository.findLatestContents(pageable);

        Page<ContentResponse> responsePage = contentPage.map(content -> ContentResponse.fromEntity(content, objectMapper));
        return PageResponse.fromPage(responsePage);
    }

    /**
     * 增加内容浏览次数
     */
    @Transactional
    public void incrementViewCount(Long contentId) {
        log.debug("增加内容浏览次数: {}", contentId);
        contentRepository.incrementViewCount(contentId);
    }

    /**
     * 增加内容点赞次数
     */
    @Transactional
    public void incrementLikeCount(Long contentId) {
        log.debug("增加内容点赞次数: {}", contentId);
        contentRepository.incrementLikeCount(contentId);
    }

    /**
     * 增加内容分享次数
     */
    @Transactional
    public void incrementShareCount(Long contentId) {
        log.debug("增加内容分享次数: {}", contentId);
        contentRepository.incrementShareCount(contentId);
    }

    /**
     * 验证内容数据格式
     */
    private void validateContentData(ContentEntity.ContentType contentType, java.util.Map<String, Object> contentData) {
        switch (contentType) {
            case ARTICLE:
                validateArticleData(contentData);
                break;
            case VIDEO:
                validateVideoData(contentData);
                break;
            case PRODUCT:
                validateProductData(contentData);
                break;
            default:
                throw new ContentException("不支持的内容类型: " + contentType);
        }
    }

    /**
     * 验证文章数据
     */
    private void validateArticleData(java.util.Map<String, Object> contentData) {
        if (!contentData.containsKey("content") || !StringUtils.hasText((String) contentData.get("content"))) {
            throw new ContentException("文章内容不能为空");
        }
        if (!contentData.containsKey("summary")) {
            contentData.put("summary", generateSummary((String) contentData.get("content")));
        }
    }

    /**
     * 验证视频数据
     */
    private void validateVideoData(java.util.Map<String, Object> contentData) {
        if (!contentData.containsKey("videoUrl") || !StringUtils.hasText((String) contentData.get("videoUrl"))) {
            throw new ContentException("视频URL不能为空");
        }
        if (!contentData.containsKey("duration")) {
            throw new ContentException("视频时长不能为空");
        }
        if (!contentData.containsKey("coverImage")) {
            throw new ContentException("视频封面不能为空");
        }
    }

    /**
     * 验证商品数据
     */
    private void validateProductData(java.util.Map<String, Object> contentData) {
        if (!contentData.containsKey("price")) {
            throw new ContentException("商品价格不能为空");
        }
        if (!contentData.containsKey("description") || !StringUtils.hasText((String) contentData.get("description"))) {
            throw new ContentException("商品描述不能为空");
        }
        if (!contentData.containsKey("images")) {
            throw new ContentException("商品图片不能为空");
        }
    }

    /**
     * 生成文章摘要
     */
    private String generateSummary(String content) {
        if (content.length() <= 200) {
            return content;
        }
        return content.substring(0, 200) + "...";
    }

    /**
     * 构建排序对象
     */
    private Sort buildSort(String sortBy, String sortDirection) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        
        // 验证排序字段
        String validSortBy;
        switch (sortBy) {
            case "publishTime":
            case "createdAt":
            case "updatedAt":
            case "hotScore":
            case "viewCount":
            case "likeCount":
                validSortBy = sortBy;
                break;
            default:
                validSortBy = "publishTime";
        }
        
        return Sort.by(direction, validSortBy);
    }
}