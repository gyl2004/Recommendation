package com.recommendation.user.service;

import com.recommendation.user.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户管理服务单元测试 - 不依赖Spring容器
 */
class UserManagementServiceUnitTest {

    @Test
    void testUserRegisterRequestValidation() {
        // 测试用户注册请求DTO的基本功能
        UserRegisterRequest request = new UserRegisterRequest();
        request.setUsername("testuser");
        request.setEmail("test@example.com");
        request.setPhone("13800138000");

        assertEquals("testuser", request.getUsername());
        assertEquals("test@example.com", request.getEmail());
        assertEquals("13800138000", request.getPhone());
    }

    @Test
    void testUserLoginRequestValidation() {
        // 测试用户登录请求DTO的基本功能
        UserLoginRequest request = new UserLoginRequest();
        request.setUsernameOrEmail("testuser");
        request.setVerificationCode("123456");

        assertEquals("testuser", request.getUsernameOrEmail());
        assertEquals("123456", request.getVerificationCode());
    }

    @Test
    void testUserUpdateRequestValidation() {
        // 测试用户更新请求DTO的基本功能
        UserUpdateRequest request = new UserUpdateRequest();
        request.setEmail("updated@example.com");
        request.setPhone("13900139000");
        
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("age", 25);
        profileData.put("interests", "tech,sports");
        request.setProfileData(profileData);

        assertEquals("updated@example.com", request.getEmail());
        assertEquals("13900139000", request.getPhone());
        assertNotNull(request.getProfileData());
        assertEquals(25, request.getProfileData().get("age"));
        assertEquals("tech,sports", request.getProfileData().get("interests"));
    }

    @Test
    void testUserResponseBuilder() {
        // 测试用户响应DTO的构建功能
        Map<String, Object> profileData = new HashMap<>();
        profileData.put("age", 30);
        profileData.put("city", "Beijing");

        UserResponse response = UserResponse.builder()
            .id(1L)
            .username("testuser")
            .email("test@example.com")
            .phone("13800138000")
            .profileData(profileData)
            .status(1)
            .build();

        assertEquals(1L, response.getId());
        assertEquals("testuser", response.getUsername());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("13800138000", response.getPhone());
        assertEquals(1, response.getStatus());
        assertNotNull(response.getProfileData());
        assertEquals(30, response.getProfileData().get("age"));
        assertEquals("Beijing", response.getProfileData().get("city"));
    }
}