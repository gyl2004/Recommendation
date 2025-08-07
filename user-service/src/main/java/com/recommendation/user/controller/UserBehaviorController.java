package com.recommendation.user.controller;

import com.recommendation.user.dto.ApiResponse;
import com.recommendation.user.dto.UserBehaviorRequest;
import com.recommendation.user.dto.UserBehaviorResponse;
import com.recommendation.user.service.UserBehaviorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户行为数据收集控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/behaviors")
@RequiredArgsConstructor
@Validated
public class UserBehaviorController {

    private final UserBehaviorService userBehaviorService;

    /**
     * 收集单个用户行为数据
     */
    @PostMapping("/collect")
    public ResponseEntity<ApiResponse<UserBehaviorResponse>> collectBehavior(
            @Valid @RequestBody UserBehaviorRequest request) {
        log.info("收到用户行为数据收集请求: userId={}, actionType={}", 
                request.getUserId(), request.getActionType());

        UserBehaviorResponse response = userBehaviorService.collectBehavior(request);
        return ResponseEntity.ok(ApiResponse.success(response, "用户行为数据收集成功"));
    }

    /**
     * 批量收集用户行为数据
     */
    @PostMapping("/collect/batch")
    public ResponseEntity<ApiResponse<List<UserBehaviorResponse>>> collectBehaviors(
            @Valid @RequestBody List<UserBehaviorRequest> requests) {
        log.info("收到批量用户行为数据收集请求: {} 条记录", requests.size());

        List<UserBehaviorResponse> responses = userBehaviorService.collectBehaviors(requests);
        return ResponseEntity.ok(ApiResponse.success(responses, "批量用户行为数据收集成功"));
    }

    /**
     * 获取用户行为历史
     */
    @GetMapping("/user/{userId}/history")
    public ResponseEntity<ApiResponse<List<UserBehaviorResponse>>> getUserBehaviorHistory(
            @PathVariable @NotNull Long userId,
            @RequestParam(defaultValue = "100") @Positive Integer limit) {
        log.info("获取用户行为历史: userId={}, limit={}", userId, limit);

        List<UserBehaviorResponse> behaviors = userBehaviorService.getUserBehaviorHistory(userId, limit);
        return ResponseEntity.ok(ApiResponse.success(behaviors));
    }

    /**
     * 获取用户在指定时间范围内的行为数据
     */
    @GetMapping("/user/{userId}/range")
    public ResponseEntity<ApiResponse<List<UserBehaviorResponse>>> getUserBehaviorsByTimeRange(
            @PathVariable @NotNull Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("获取用户时间范围内行为数据: userId={}, startTime={}, endTime={}", 
                userId, startTime, endTime);

        List<UserBehaviorResponse> behaviors = userBehaviorService
                .getUserBehaviorsByTimeRange(userId, startTime, endTime);
        return ResponseEntity.ok(ApiResponse.success(behaviors));
    }

    /**
     * 获取内容的行为统计
     */
    @GetMapping("/content/{contentId}")
    public ResponseEntity<ApiResponse<List<UserBehaviorResponse>>> getContentBehaviors(
            @PathVariable @NotNull Long contentId) {
        log.info("获取内容行为统计: contentId={}", contentId);

        List<UserBehaviorResponse> behaviors = userBehaviorService.getContentBehaviors(contentId);
        return ResponseEntity.ok(ApiResponse.success(behaviors));
    }

    /**
     * 统计用户活跃度
     */
    @GetMapping("/user/{userId}/activity")
    public ResponseEntity<ApiResponse<Long>> getUserActivityCount(
            @PathVariable @NotNull Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        log.info("统计用户活跃度: userId={}, startTime={}, endTime={}", userId, startTime, endTime);

        long activityCount = userBehaviorService.getUserActivityCount(userId, startTime, endTime);
        return ResponseEntity.ok(ApiResponse.success(activityCount));
    }

    /**
     * 获取热门内容
     */
    @GetMapping("/popular")
    public ResponseEntity<ApiResponse<List<Object[]>>> getPopularContent(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {
        log.info("获取热门内容: since={}", since);

        if (since == null) {
            since = LocalDateTime.now().minusDays(7); // 默认最近7天
        }

        List<Object[]> popularContent = userBehaviorService.getPopularContent(since);
        return ResponseEntity.ok(ApiResponse.success(popularContent));
    }
}