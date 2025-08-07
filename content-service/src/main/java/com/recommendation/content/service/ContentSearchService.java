package com.recommendation.content.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.content.document.ContentDocument;
import com.recommendation.content.dto.ContentResponse;
import com.recommendation.content.entity.ContentEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内容搜索服务
 * 整合Elasticsearch搜索功能与内容管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSearchService {

    private final ElasticsearchService elasticsearchService;
    private final ObjectMapper objectMapper;

    /**
     * 将内容实体转换为Elasticsearch文档
     */
    public ContentDocument convertToDocument(ContentEntity entity) {
        try {
            // 解析标签JSON字符串
            List<String> tagsList = null;
            if (entity.getTags() != null) {
                tagsList = objectMapper.readValue(entity.getTags(), 
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }

            // 解析内容数据JSON字符串
            Map<String, Object> contentDataMap = null;
            if (entity.getContentData() != null) {
                contentDataMap = objectMapper.readValue(entity.getContentData(), Map.class);
            }

            return ContentDocument.builder()
                    .contentId(entity.getId())
                    .title(entity.getTitle())
                    .contentType(entity.getContentType().name().toLowerCase())
                    .tags(tagsList)
                    .category(entity.getCategoryId() != null ? entity.getCategoryId().toString() : null)
                    .publishTime(entity.getPublishTime())
                    .hotScore(calculateHotScore(entity))
                    .authorId(entity.getAuthorId())
                    .summary(extractSummary(entity, contentDataMap))
                    .extraData(contentDataMap)
                    .createdAt(entity.getCreatedAt())
                    .updatedAt(entity.getUpdatedAt())
                    .build();
        } catch (Exception e) {
            log.error("Failed to convert entity to document: {}", entity.getId(), e);
            throw new RuntimeException("Failed to convert entity to document", e);
        }
    }

    /**
     * 将Elasticsearch文档转换为响应DTO
     */
    public ContentResponse convertToResponse(ContentDocument document) {
        // 将字符串类型转换为枚举类型
        ContentEntity.ContentType contentType = null;
        if (document.getContentType() != null) {
            try {
                contentType = ContentEntity.ContentType.valueOf(document.getContentType().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown content type: {}", document.getContentType());
                contentType = ContentEntity.ContentType.ARTICLE; // 默认类型
            }
        }

        return ContentResponse.builder()
                .id(document.getContentId())
                .title(document.getTitle())
                .contentType(contentType)
                .tags(document.getTags())
                .categoryId(document.getCategory() != null ? Integer.parseInt(document.getCategory()) : null)
                .publishTime(document.getPublishTime())
                .authorId(document.getAuthorId())
                .contentData(document.getExtraData())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    /**
     * 索引内容到Elasticsearch
     */
    public void indexContent(ContentEntity entity) {
        try {
            ContentDocument document = convertToDocument(entity);
            elasticsearchService.indexDocument(document);
            log.info("Successfully indexed content: {}", entity.getId());
        } catch (IOException e) {
            log.error("Failed to index content: {}", entity.getId(), e);
            throw new RuntimeException("Failed to index content", e);
        }
    }

    /**
     * 批量索引内容
     */
    public void bulkIndexContent(List<ContentEntity> entities) {
        try {
            List<ContentDocument> documents = entities.stream()
                    .map(this::convertToDocument)
                    .collect(Collectors.toList());
            
            elasticsearchService.bulkIndexDocuments(documents);
            log.info("Successfully bulk indexed {} contents", entities.size());
        } catch (IOException e) {
            log.error("Failed to bulk index contents", e);
            throw new RuntimeException("Failed to bulk index contents", e);
        }
    }

    /**
     * 更新内容索引
     */
    public void updateContentIndex(ContentEntity entity) {
        try {
            ContentDocument document = convertToDocument(entity);
            elasticsearchService.updateDocument(entity.getId().toString(), document);
            log.info("Successfully updated content index: {}", entity.getId());
        } catch (IOException e) {
            log.error("Failed to update content index: {}", entity.getId(), e);
            throw new RuntimeException("Failed to update content index", e);
        }
    }

    /**
     * 删除内容索引
     */
    public void deleteContentIndex(Long contentId) {
        try {
            elasticsearchService.deleteDocument(contentId.toString());
            log.info("Successfully deleted content index: {}", contentId);
        } catch (IOException e) {
            log.error("Failed to delete content index: {}", contentId, e);
            throw new RuntimeException("Failed to delete content index", e);
        }
    }

    /**
     * 搜索内容
     */
    public List<ContentResponse> searchContent(String query, String contentType, 
                                             List<String> tags, int page, int size) {
        try {
            int from = page * size;
            List<ContentDocument> documents = elasticsearchService.searchContent(
                    query, contentType, tags, from, size);
            
            return documents.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to search content", e);
            throw new RuntimeException("Failed to search content", e);
        }
    }

    /**
     * 搜索相似内容
     */
    public List<ContentResponse> searchSimilarContent(List<Float> queryVector, 
                                                    String contentType, int size) {
        try {
            List<ContentDocument> documents = elasticsearchService.searchSimilarContent(
                    queryVector, contentType, size);
            
            return documents.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to search similar content", e);
            throw new RuntimeException("Failed to search similar content", e);
        }
    }

    /**
     * 获取热门内容
     */
    public List<ContentResponse> getHotContent(String contentType, int size) {
        try {
            List<ContentDocument> documents = elasticsearchService.getHotContent(contentType, size);
            
            return documents.stream()
                    .map(this::convertToResponse)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("Failed to get hot content", e);
            throw new RuntimeException("Failed to get hot content", e);
        }
    }

    /**
     * 计算内容热度分数
     * 基于发布时间、互动数据等因素计算
     */
    private Float calculateHotScore(ContentEntity entity) {
        float score = 0.0f;
        
        // 基础分数
        score += 10.0f;
        
        // 互动数据加分
        if (entity.getViewCount() != null) {
            score += entity.getViewCount() * 0.1f;
        }
        if (entity.getLikeCount() != null) {
            score += entity.getLikeCount() * 2.0f;
        }
        if (entity.getShareCount() != null) {
            score += entity.getShareCount() * 5.0f;
        }
        if (entity.getCommentCount() != null) {
            score += entity.getCommentCount() * 3.0f;
        }
        
        // 时间衰减因子（越新的内容分数越高）
        if (entity.getPublishTime() != null) {
            long hoursAgo = java.time.Duration.between(entity.getPublishTime(), LocalDateTime.now()).toHours();
            score += Math.max(0, 100 - hoursAgo * 0.1f);
        }
        
        // 使用实体中已计算的热度分数
        if (entity.getHotScore() != null) {
            score += entity.getHotScore().floatValue();
        }
        
        return score;
    }

    /**
     * 提取内容摘要
     */
    private String extractSummary(ContentEntity entity, Map<String, Object> contentDataMap) {
        if (contentDataMap == null) {
            return entity.getTitle();
        }
        
        // 根据内容类型提取摘要
        switch (entity.getContentType()) {
            case ARTICLE:
                Object content = contentDataMap.get("content");
                if (content != null) {
                    String text = content.toString();
                    return text.length() > 200 ? text.substring(0, 200) + "..." : text;
                }
                Object summary = contentDataMap.get("summary");
                if (summary != null) {
                    return summary.toString();
                }
                break;
            case VIDEO:
                Object description = contentDataMap.get("description");
                return description != null ? description.toString() : entity.getTitle();
            case PRODUCT:
                Object productDesc = contentDataMap.get("description");
                return productDesc != null ? productDesc.toString() : entity.getTitle();
        }
        
        return entity.getTitle();
    }
}