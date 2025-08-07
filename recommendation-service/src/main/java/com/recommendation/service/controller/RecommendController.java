package com.recommendation.service.controller;

import com.recommendation.service.dto.RecommendRequest;
import com.recommendation.service.dto.RecommendResponse;
import com.recommendation.service.dto.FeedbackRequest;
import com.recommendation.service.service.RecommendationService;
import com.recommendation.service.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Min;
import javax.validation.constraints.Max;
import javax.validation.constraints.Pattern;
import java.util.UUID;

/**
 * 推荐服务控制器
 */
@Slf4j
@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
@Validated
public class RecommendController {
    
    private final RecommendationService recommendationService;
    private final FeedbackService feedbackService;
    
    /**
     * 获取个性化推荐内容
     */
    @GetMapping("/content")
    public ResponseEntity<RecommendResponse> recommendContent(
            @RequestParam @NotBlank(message = "用户ID不能为空") String userId,
            @RequestParam(defaultValue = "10") @Min(value = 1) @Max(value = 100) Integer size,
            @RequestParam(defaultValue = "mixed") 
            @Pattern(regexp = "^(article|video|product|mixed)$") String contentType,
            @RequestParam(defaultValue = "default") String scene,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String location) {
        
        String requestId = UUID.randomUUID().toString();
        log.info("收到推荐请求 - requestId: {}, userId: {}, size: {}, contentType: {}, scene: {}", 
                requestId, userId, size, contentType, scene);
        
        try {
            RecommendRequest request = RecommendRequest.builder()
                    .userId(userId)
                    .size(size)
                    .contentType(contentType)
                    .scene(scene)
                    .deviceType(deviceType)
                    .location(location)
                    .build();
            
            RecommendResponse response = recommendationService.recommend(request, requestId);
            
            log.info("推荐请求完成 - requestId: {}, 返回{}个结果, 来自缓存: {}", 
                    requestId, response.getTotal(), response.getFromCache());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("推荐请求失败 - requestId: {}, error: {}", requestId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 批量推荐接口
     */
    @PostMapping("/batch")
    public ResponseEntity<RecommendResponse> batchRecommend(@Valid @RequestBody RecommendRequest request) {
        
        String requestId = UUID.randomUUID().toString();
        log.info("收到批量推荐请求 - requestId: {}, userId: {}", requestId, request.getUserId());
        
        try {
            RecommendResponse response = recommendationService.recommend(request, requestId);
            
            log.info("批量推荐请求完成 - requestId: {}, 返回{}个结果", requestId, response.getTotal());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("批量推荐请求失败 - requestId: {}, error: {}", requestId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 记录用户反馈
     */
    @PostMapping("/feedback")
    public ResponseEntity<Void> recordFeedback(@Valid @RequestBody FeedbackRequest request) {
        
        log.info("收到用户反馈 - userId: {}, contentId: {}, feedbackType: {}", 
                request.getUserId(), request.getContentId(), request.getFeedbackType());
        
        try {
            feedbackService.recordFeedback(request);
            
            log.info("用户反馈记录成功 - userId: {}, contentId: {}", 
                    request.getUserId(), request.getContentId());
            
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("用户反馈记录失败 - userId: {}, contentId: {}, error: {}", 
                    request.getUserId(), request.getContentId(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 获取推荐解释
     */
    @GetMapping("/explain")
    public ResponseEntity<String> explainRecommendation(
            @RequestParam @NotBlank String userId,
            @RequestParam @NotBlank String contentId,
            @RequestParam(required = false) String requestId) {
        
        log.info("获取推荐解释 - userId: {}, contentId: {}, requestId: {}", 
                userId, contentId, requestId);
        
        try {
            String explanation = recommendationService.explainRecommendation(userId, contentId, requestId);
            return ResponseEntity.ok(explanation);
            
        } catch (Exception e) {
            log.error("获取推荐解释失败 - userId: {}, contentId: {}, error: {}", 
                    userId, contentId, e.getMessage(), e);
            throw e;
        }
    }
}