package com.recommendation.user.controller;

import com.recommendation.user.dto.ApiResponse;
import com.recommendation.user.dto.*;
import com.recommendation.user.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 用户管理控制器
 * 提供用户注册、登录、信息管理等API接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserManagementService userManagementService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> registerUser(
            @Valid @RequestBody UserRegisterRequest request) {
        log.info("收到用户注册请求: {}", request.getUsername());
        
        UserResponse userResponse = userManagementService.registerUser(request);
        return ResponseEntity.ok(ApiResponse.success(userResponse, "用户注册成功"));
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserResponse>> loginUser(
            @Valid @RequestBody UserLoginRequest request) {
        log.info("收到用户登录请求: {}", request.getUsernameOrEmail());
        
        UserResponse userResponse = userManagementService.loginUser(request);
        return ResponseEntity.ok(ApiResponse.success(userResponse, "用户登录成功"));
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable @NotNull Long userId,
            @Valid @RequestBody UserUpdateRequest request) {
        log.info("收到用户信息更新请求: {}", userId);
        
        UserResponse userResponse = userManagementService.updateUser(userId, request);
        return ResponseEntity.ok(ApiResponse.success(userResponse, "用户信息更新成功"));
    }

    /**
     * 根据ID获取用户信息
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable @NotNull Long userId) {
        log.info("获取用户信息: {}", userId);
        
        UserResponse userResponse = userManagementService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.success(userResponse));
    }

    /**
     * 根据用户名获取用户信息
     */
    @GetMapping("/username/{username}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserByUsername(
            @PathVariable String username) {
        log.info("根据用户名获取用户信息: {}", username);
        
        UserResponse userResponse = userManagementService.getUserByUsername(username);
        return ResponseEntity.ok(ApiResponse.success(userResponse));
    }

    /**
     * 获取所有激活用户
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getActiveUsers() {
        log.info("获取所有激活用户");
        
        List<UserResponse> users = userManagementService.getActiveUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * 禁用用户
     */
    @PutMapping("/{userId}/disable")
    public ResponseEntity<ApiResponse<Void>> disableUser(
            @PathVariable @NotNull Long userId) {
        log.info("禁用用户: {}", userId);
        
        userManagementService.disableUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "用户禁用成功"));
    }

    /**
     * 启用用户
     */
    @PutMapping("/{userId}/enable")
    public ResponseEntity<ApiResponse<Void>> enableUser(
            @PathVariable @NotNull Long userId) {
        log.info("启用用户: {}", userId);
        
        userManagementService.enableUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "用户启用成功"));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable @NotNull Long userId) {
        log.info("删除用户: {}", userId);
        
        userManagementService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.success(null, "用户删除成功"));
    }
}