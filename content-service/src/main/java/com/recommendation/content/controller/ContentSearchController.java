package com.recommendation.content.controller;

import com.recommendation.content.dto.ApiResponse;
import com.recommendation.content.dto.ContentResponse;
import com.recommendation.content.dto.PageResponse;
import com.recommendation.content.service.ContentSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 内容搜索控制器
 * 提供内容搜索和相似度计算的API接口
 */
@Slf4j
@RestController
@RequestMapping("/content/search")
@RequiredArgsConstructor
@Tag(name = "内容搜索", description = "内容搜索和相似度计算API")
public class ContentSearchController {

    private final ContentSearchService contentSearchService;

    /**
     * 搜索内容
     */
    @GetMapping
    @Operation(summary = "搜索内容", description = "根据关键词、内容类型、标签等条件搜索内容")
    public ResponseEntity<ApiResponse<PageResponse<ContentResponse>>> searchContent(
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String query,
            @Parameter(description = "内容类型") @RequestParam(required = false) String contentType,
            @Parameter(description = "标签列表") @RequestParam(required = false) List<String> tags,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        
        try {
            List<ContentResponse> contents = contentSearchService.searchContent(
                    query, contentType, tags, page, size);
            
            PageResponse<ContentResponse> pageResponse = PageResponse.<ContentResponse>builder()
                    .content(contents)
                    .page(page)
                    .size(size)
                    .totalElements((long) contents.size())
                    .totalPages((contents.size() + size - 1) / size)
                    .build();
            
            return ResponseEntity.ok(ApiResponse.success(pageResponse));
        } catch (Exception e) {
            log.error("Failed to search content", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("搜索内容失败: " + e.getMessage()));
        }
    }

    /**
     * 相似内容推荐
     */
    @PostMapping("/similar")
    @Operation(summary = "相似内容搜索", description = "基于向量相似度搜索相似内容")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> searchSimilarContent(
            @Parameter(description = "查询向量") @RequestBody List<Float> queryVector,
            @Parameter(description = "内容类型") @RequestParam(required = false) String contentType,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") int size) {
        
        try {
            List<ContentResponse> contents = contentSearchService.searchSimilarContent(
                    queryVector, contentType, size);
            
            return ResponseEntity.ok(ApiResponse.success(contents));
        } catch (Exception e) {
            log.error("Failed to search similar content", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("搜索相似内容失败: " + e.getMessage()));
        }
    }

    /**
     * 获取热门内容
     */
    @GetMapping("/hot")
    @Operation(summary = "获取热门内容", description = "获取热门内容列表")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> getHotContent(
            @Parameter(description = "内容类型") @RequestParam(required = false) String contentType,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "20") int size) {
        
        try {
            List<ContentResponse> contents = contentSearchService.getHotContent(contentType, size);
            
            return ResponseEntity.ok(ApiResponse.success(contents));
        } catch (Exception e) {
            log.error("Failed to get hot content", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("获取热门内容失败: " + e.getMessage()));
        }
    }

    /**
     * 内容推荐接口（基于内容ID的相似推荐）
     */
    @GetMapping("/{contentId}/recommendations")
    @Operation(summary = "内容推荐", description = "基于指定内容推荐相似内容")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> getContentRecommendations(
            @Parameter(description = "内容ID") @PathVariable Long contentId,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") int size) {
        
        try {
            // 这里可以根据内容ID获取其向量表示，然后进行相似度搜索
            // 暂时返回热门内容作为推荐
            List<ContentResponse> contents = contentSearchService.getHotContent(null, size);
            
            return ResponseEntity.ok(ApiResponse.success(contents));
        } catch (Exception e) {
            log.error("Failed to get content recommendations for contentId: {}", contentId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("获取内容推荐失败: " + e.getMessage()));
        }
    }

    /**
     * 多类型内容搜索
     */
    @PostMapping("/multi-type")
    @Operation(summary = "多类型内容搜索", description = "同时搜索多种类型的内容")
    public ResponseEntity<ApiResponse<List<ContentResponse>>> searchMultiTypeContent(
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String query,
            @Parameter(description = "内容类型列表") @RequestBody List<String> contentTypes,
            @Parameter(description = "每种类型返回数量") @RequestParam(defaultValue = "5") int sizePerType) {
        
        try {
            List<ContentResponse> allContents = new java.util.ArrayList<>();
            
            for (String contentType : contentTypes) {
                List<ContentResponse> contents = contentSearchService.searchContent(
                        query, contentType, null, 0, sizePerType);
                allContents.addAll(contents);
            }
            
            return ResponseEntity.ok(ApiResponse.success(allContents));
        } catch (Exception e) {
            log.error("Failed to search multi-type content", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("多类型内容搜索失败: " + e.getMessage()));
        }
    }
}