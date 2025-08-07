package com.recommendation.user.controller;

import com.recommendation.user.dto.ApiResponse;
import com.recommendation.user.dto.UserProfileRequest;
import com.recommendation.user.dto.UserProfileResponse;
import com.recommendation.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 * 用户画像控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
@Validated
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 构建用户画像
     */
    @PostMapping("/build/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> buildUserProfile(
            @PathVariable @NotNull Long userId) {
        log.info("收到构建用户画像请求: userId={}", userId);

        UserProfileResponse profile = userProfileService.buildUserProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile, "用户画像构建成功"));
    }

    /**
     * 更新用户画像
     */
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateUserProfile(
            @PathVariable @NotNull Long userId,
            @Valid @RequestBody UserProfileRequest request) {
        log.info("收到更新用户画像请求: userId={}", userId);

        // 确保请求中的用户ID与路径参数一致
        request.setUserId(userId);

        UserProfileResponse profile = userProfileService.updateUserProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success(profile, "用户画像更新成功"));
    }

    /**
     * 获取用户画像
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @PathVariable @NotNull Long userId) {
        log.info("获取用户画像: userId={}", userId);

        UserProfileResponse profile = userProfileService.getUserProfile(userId);
        if (profile == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "用户画像不存在"));
        }

        return ResponseEntity.ok(ApiResponse.success(profile));
    }

    /**
     * 重新构建用户画像（强制刷新）
     */
    @PostMapping("/rebuild/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> rebuildUserProfile(
            @PathVariable @NotNull Long userId) {
        log.info("收到重新构建用户画像请求: userId={}", userId);

        UserProfileResponse profile = userProfileService.buildUserProfile(userId);
        return ResponseEntity.ok(ApiResponse.success(profile, "用户画像重新构建成功"));
    }
}