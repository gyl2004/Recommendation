package com.recommendation.content.controller;

import com.recommendation.content.dto.ApiResponse;
import com.recommendation.content.entity.ContentEntity;
import com.recommendation.content.dto.*;
import com.recommendation.content.service.ContentManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 内容管理控制器
 * 实现需求2.1-2.4: 多类型内容管理API
 */
@Slf4j
@RestController
@RequestMapping("/contents")
@RequiredArgsConstructor
@Validated
@Tag(name = "内容管理", description = "内容的创建、更新、查询和删除操作")
public class ContentController {

    private final ContentManagementService contentManagementService;

    /**
     * 创建内容
     */
    @PostMapping
    @Operation(summary = "创建内容", description = "创建文章、视频或商品内容")
    public ResponseEntity<ApiResponse<ContentResponse>> createContent(
            @Valid @RequestBody ContentCreateRequest request) {
        log.info("创建内容请求: {}", request.getTitle());
        
        ContentResponse response = contentManagementService.createContent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "内容创建成功"));
    }

    /**
     * 更新内容
     */
    @PutMapping("/{contentId}")
    @Operation(summary = "更新内容", description = "更新指定ID的内容信息")
    public ResponseEntity<ApiResponse<ContentResponse>> updateContent(
            @Parameter(description = "内容ID") @PathVariable Long contentId,
            @Valid @RequestBody ContentUpdateRequest request) {
        log.info("更新内容请求: {}", contentId);
        
        request.setId(contentId);
        ContentResponse response = contentManagementService.updateContent(request);
        return ResponseEntity.ok(ApiResponse.success(response, "内容更新成功"));
    }

    /**
     * 获取内容详情
     */
    @GetMapping("/{contentId}")
    @Operation(summary = "获取内容详情", description = "根据ID获取内容的详细信息")
    public ResponseEntity<ApiResponse<ContentResponse>> getContent(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        log.debug("获取内容详情: {}", contentId);
        
        ContentResponse response = contentManagementService.getContentById(contentId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 查询内容列表
     */
    @GetMapping
    @Operation(summary = "查询内容列表", description = "根据条件分页查询内容列表")
    public ResponseEntity<ApiResponse<PageResponse<ContentResponse>>> queryContents(
            @Parameter(description = "内容类型") @RequestParam(required = false) ContentEntity.ContentType contentType,
            @Parameter(description = "分类ID") @RequestParam(required = false) Integer categoryId,
            @Parameter(description = "作者ID") @RequestParam(required = false) Long authorId,
            @Parameter(description = "内容状态") @RequestParam(required = false, defaultValue = "PUBLISHED") ContentEntity.ContentStatus status,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "排序字段") @RequestParam(required = false, defaultValue = "publishTime") String sortBy,
            @Parameter(description = "排序方向") @RequestParam(required = false, defaultValue = "desc") String sortDirection,
            @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "页大小") @RequestParam(required = false, defaultValue = "20") Integer size) {
        
        ContentQueryRequest request = new ContentQueryRequest();
        request.setContentType(contentType);
        request.setCategoryId(categoryId);
        request.setAuthorId(authorId);
        request.setStatus(status);
        request.setKeyword(keyword);
        request.setSortBy(sortBy);
        request.setSortDirection(sortDirection);
        request.setPage(page);
        request.setSize(size);
        
        PageResponse<ContentResponse> response = contentManagementService.queryContents(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 删除内容
     */
    @DeleteMapping("/{contentId}")
    @Operation(summary = "删除内容", description = "删除指定ID的内容")
    public ResponseEntity<ApiResponse<Void>> deleteContent(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        log.info("删除内容: {}", contentId);
        
        contentManagementService.deleteContent(contentId);
        return ResponseEntity.ok(ApiResponse.success(null, "内容删除成功"));
    }

    /**
     * 批量更新内容状态
     */
    @PatchMapping("/status")
    @Operation(summary = "批量更新状态", description = "批量更新多个内容的状态")
    public ResponseEntity<ApiResponse<Void>> batchUpdateStatus(
            @Parameter(description = "内容ID列表") @RequestParam List<Long> contentIds,
            @Parameter(description = "目标状态") @RequestParam ContentEntity.ContentStatus status) {
        log.info("批量更新内容状态: {} -> {}", contentIds, status);
        
        contentManagementService.batchUpdateStatus(contentIds, status);
        return ResponseEntity.ok(ApiResponse.success(null, "状态更新成功"));
    }

    /**
     * 获取热门内容
     */
    @GetMapping("/hot")
    @Operation(summary = "获取热门内容", description = "获取热门内容列表")
    public ResponseEntity<ApiResponse<PageResponse<ContentResponse>>> getHotContents(
            @Parameter(description = "内容类型") @RequestParam(required = false) ContentEntity.ContentType contentType,
            @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "页大小") @RequestParam(required = false, defaultValue = "20") Integer size) {
        
        PageResponse<ContentResponse> response = contentManagementService.getHotContents(contentType, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 获取最新内容
     */
    @GetMapping("/latest")
    @Operation(summary = "获取最新内容", description = "获取最新发布的内容列表")
    public ResponseEntity<ApiResponse<PageResponse<ContentResponse>>> getLatestContents(
            @Parameter(description = "内容类型") @RequestParam(required = false) ContentEntity.ContentType contentType,
            @Parameter(description = "页码") @RequestParam(required = false, defaultValue = "0") Integer page,
            @Parameter(description = "页大小") @RequestParam(required = false, defaultValue = "20") Integer size) {
        
        PageResponse<ContentResponse> response = contentManagementService.getLatestContents(contentType, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 增加浏览次数
     */
    @PostMapping("/{contentId}/view")
    @Operation(summary = "增加浏览次数", description = "记录内容浏览行为")
    public ResponseEntity<ApiResponse<Void>> incrementViewCount(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        log.debug("增加浏览次数: {}", contentId);
        
        contentManagementService.incrementViewCount(contentId);
        return ResponseEntity.ok(ApiResponse.success(null, "浏览次数更新成功"));
    }

    /**
     * 增加点赞次数
     */
    @PostMapping("/{contentId}/like")
    @Operation(summary = "增加点赞次数", description = "记录内容点赞行为")
    public ResponseEntity<ApiResponse<Void>> incrementLikeCount(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        log.debug("增加点赞次数: {}", contentId);
        
        contentManagementService.incrementLikeCount(contentId);
        return ResponseEntity.ok(ApiResponse.success(null, "点赞次数更新成功"));
    }

    /**
     * 增加分享次数
     */
    @PostMapping("/{contentId}/share")
    @Operation(summary = "增加分享次数", description = "记录内容分享行为")
    public ResponseEntity<ApiResponse<Void>> incrementShareCount(
            @Parameter(description = "内容ID") @PathVariable Long contentId) {
        log.debug("增加分享次数: {}", contentId);
        
        contentManagementService.incrementShareCount(contentId);
        return ResponseEntity.ok(ApiResponse.success(null, "分享次数更新成功"));
    }
}