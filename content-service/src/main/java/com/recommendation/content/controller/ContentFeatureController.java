package com.recommendation.content.controller;

import com.recommendation.content.dto.ApiResponse;
import com.recommendation.content.dto.ContentFeatures;
import com.recommendation.content.entity.ContentEntity;
import com.recommendation.content.repository.ContentEntityRepository;
import com.recommendation.content.service.ContentFeatureExtractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 内容特征提取控制器
 * 实现需求6.1, 6.2, 6.3: 特征工程处理
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/content/features")
@RequiredArgsConstructor
@Tag(name = "内容特征提取", description = "内容特征提取相关API")
public class ContentFeatureController {

    private final ContentFeatureExtractionService featureExtractionService;
    private final ContentEntityRepository contentRepository;

    @Operation(summary = "提取内容特征", description = "根据内容ID提取完整的内容特征")
    @GetMapping("/{contentId}")
    public ResponseEntity<ApiResponse<ContentFeatures>> extractContentFeatures(
            @Parameter(description = "内容ID", required = true)
            @PathVariable Long contentId) {
        
        log.info("提取内容特征请求: contentId={}", contentId);

        try {
            // 查找内容
            ContentEntity content = contentRepository.findById(contentId)
                    .orElseThrow(() -> new RuntimeException("内容不存在: " + contentId));

            // 提取特征
            ContentFeatures features = featureExtractionService.extractFeatures(content);

            log.info("内容特征提取成功: contentId={}", contentId);
            return ResponseEntity.ok(ApiResponse.success(features));

        } catch (Exception e) {
            log.error("内容特征提取失败: contentId={}", contentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("内容特征提取失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "提取文本特征", description = "提取指定内容的文本特征（TF-IDF和词向量）")
    @GetMapping("/{contentId}/text")
    public ResponseEntity<ApiResponse<ContentFeatures.TextFeatures>> extractTextFeatures(
            @Parameter(description = "内容ID", required = true)
            @PathVariable Long contentId) {
        
        log.info("提取文本特征请求: contentId={}", contentId);

        try {
            // 查找内容
            ContentEntity content = contentRepository.findById(contentId)
                    .orElseThrow(() -> new RuntimeException("内容不存在: " + contentId));

            // 根据内容类型提取文本
            String text = extractTextFromContent(content);
            
            // 提取文本特征
            ContentFeatures.TextFeatures textFeatures = 
                    featureExtractionService.extractTextFeatures(content, text);

            log.info("文本特征提取成功: contentId={}", contentId);
            return ResponseEntity.ok(ApiResponse.success(textFeatures));

        } catch (Exception e) {
            log.error("文本特征提取失败: contentId={}", contentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("文本特征提取失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "提取视频特征", description = "提取指定视频内容的元数据特征")
    @GetMapping("/{contentId}/video")
    public ResponseEntity<ApiResponse<ContentFeatures.VideoFeatures>> extractVideoFeatures(
            @Parameter(description = "内容ID", required = true)
            @PathVariable Long contentId) {
        
        log.info("提取视频特征请求: contentId={}", contentId);

        try {
            // 查找内容
            ContentEntity content = contentRepository.findById(contentId)
                    .orElseThrow(() -> new RuntimeException("内容不存在: " + contentId));

            // 验证内容类型
            if (content.getContentType() != ContentEntity.ContentType.VIDEO) {
                throw new RuntimeException("内容类型不是视频: " + content.getContentType());
            }

            // 提取视频特征
            ContentFeatures.VideoFeatures videoFeatures = 
                    featureExtractionService.extractVideoFeatures(content);

            log.info("视频特征提取成功: contentId={}", contentId);
            return ResponseEntity.ok(ApiResponse.success(videoFeatures));

        } catch (Exception e) {
            log.error("视频特征提取失败: contentId={}", contentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("视频特征提取失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "提取商品特征", description = "提取指定商品内容的属性特征")
    @GetMapping("/{contentId}/product")
    public ResponseEntity<ApiResponse<ContentFeatures.ProductFeatures>> extractProductFeatures(
            @Parameter(description = "内容ID", required = true)
            @PathVariable Long contentId) {
        
        log.info("提取商品特征请求: contentId={}", contentId);

        try {
            // 查找内容
            ContentEntity content = contentRepository.findById(contentId)
                    .orElseThrow(() -> new RuntimeException("内容不存在: " + contentId));

            // 验证内容类型
            if (content.getContentType() != ContentEntity.ContentType.PRODUCT) {
                throw new RuntimeException("内容类型不是商品: " + content.getContentType());
            }

            // 提取商品特征
            ContentFeatures.ProductFeatures productFeatures = 
                    featureExtractionService.extractProductFeatures(content);

            log.info("商品特征提取成功: contentId={}", contentId);
            return ResponseEntity.ok(ApiResponse.success(productFeatures));

        } catch (Exception e) {
            log.error("商品特征提取失败: contentId={}", contentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("商品特征提取失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "提取通用特征", description = "提取指定内容的通用特征")
    @GetMapping("/{contentId}/common")
    public ResponseEntity<ApiResponse<ContentFeatures.CommonFeatures>> extractCommonFeatures(
            @Parameter(description = "内容ID", required = true)
            @PathVariable Long contentId) {
        
        log.info("提取通用特征请求: contentId={}", contentId);

        try {
            // 查找内容
            ContentEntity content = contentRepository.findById(contentId)
                    .orElseThrow(() -> new RuntimeException("内容不存在: " + contentId));

            // 提取通用特征
            ContentFeatures.CommonFeatures commonFeatures = 
                    featureExtractionService.extractCommonFeatures(content);

            log.info("通用特征提取成功: contentId={}", contentId);
            return ResponseEntity.ok(ApiResponse.success(commonFeatures));

        } catch (Exception e) {
            log.error("通用特征提取失败: contentId={}", contentId, e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("通用特征提取失败: " + e.getMessage()));
        }
    }

    @Operation(summary = "批量提取特征", description = "批量提取多个内容的特征")
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<java.util.Map<Long, ContentFeatures>>> batchExtractFeatures(
            @Parameter(description = "内容ID列表", required = true)
            @RequestBody java.util.List<Long> contentIds) {
        
        log.info("批量提取特征请求: contentIds={}", contentIds);

        try {
            java.util.Map<Long, ContentFeatures> featuresMap = new java.util.HashMap<>();

            for (Long contentId : contentIds) {
                try {
                    ContentEntity content = contentRepository.findById(contentId).orElse(null);
                    if (content != null) {
                        ContentFeatures features = featureExtractionService.extractFeatures(content);
                        featuresMap.put(contentId, features);
                    } else {
                        log.warn("内容不存在，跳过: contentId={}", contentId);
                    }
                } catch (Exception e) {
                    log.error("单个内容特征提取失败: contentId={}", contentId, e);
                    // 继续处理其他内容
                }
            }

            log.info("批量特征提取完成: 成功={}, 总数={}", featuresMap.size(), contentIds.size());
            return ResponseEntity.ok(ApiResponse.success(featuresMap));

        } catch (Exception e) {
            log.error("批量特征提取失败", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("批量特征提取失败: " + e.getMessage()));
        }
    }

    /**
     * 从内容中提取文本
     */
    private String extractTextFromContent(ContentEntity content) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> contentData = objectMapper.readValue(
                    content.getContentData(), 
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {}
            );

            StringBuilder text = new StringBuilder();
            text.append(content.getTitle()).append(" ");

            switch (content.getContentType()) {
                case ARTICLE:
                    String articleContent = (String) contentData.get("content");
                    String summary = (String) contentData.get("summary");
                    if (summary != null) text.append(summary).append(" ");
                    if (articleContent != null) text.append(articleContent);
                    break;
                case PRODUCT:
                    String description = (String) contentData.get("description");
                    if (description != null) text.append(description);
                    break;
                case VIDEO:
                    String videoDescription = (String) contentData.get("description");
                    if (videoDescription != null) text.append(videoDescription);
                    break;
            }

            return text.toString();

        } catch (Exception e) {
            log.error("文本提取失败: contentId={}", content.getId(), e);
            return content.getTitle();
        }
    }
}