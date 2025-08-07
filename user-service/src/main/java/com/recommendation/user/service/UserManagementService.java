package com.recommendation.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.user.entity.UserEntity;
import com.recommendation.user.exception.UserServiceException;
import com.recommendation.user.repository.UserEntityRepository;
import com.recommendation.user.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 * 提供用户注册、登录、信息更新等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final UserEntityRepository userRepository;
    private final ObjectMapper objectMapper;

    /**
     * 用户注册
     */
    @Transactional
    public UserResponse registerUser(UserRegisterRequest request) {
        log.info("开始注册用户: {}", request.getUsername());

        // 检查用户名是否已存在
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserServiceException("用户名已存在");
        }

        // 检查邮箱是否已存在
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new UserServiceException("邮箱已存在");
        }

        // 检查手机号是否已存在
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new UserServiceException("手机号已存在");
        }

        // 创建用户实体
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setStatus(1); // 默认激活状态

        // 保存用户
        UserEntity savedUser = userRepository.save(user);
        log.info("用户注册成功: {}", savedUser.getId());

        return convertToUserResponse(savedUser);
    }

    /**
     * 用户登录
     */
    public UserResponse loginUser(UserLoginRequest request) {
        log.info("用户登录: {}", request.getUsernameOrEmail());

        // 根据用户名或邮箱查找用户
        Optional<UserEntity> userOpt = userRepository.findByUsernameOrEmail(
            request.getUsernameOrEmail(), 
            request.getUsernameOrEmail()
        );

        if (!userOpt.isPresent()) {
            throw new UserServiceException("用户不存在");
        }

        UserEntity user = userOpt.get();
        if (!user.isActive()) {
            throw new UserServiceException("用户已被禁用");
        }

        // 这里可以添加验证码验证逻辑
        // 简化处理，实际项目中需要验证密码或验证码

        log.info("用户登录成功: {}", user.getId());
        return convertToUserResponse(user);
    }

    /**
     * 更新用户信息
     */
    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        log.info("更新用户信息: {}", userId);

        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new UserServiceException("用户不存在"));

        // 检查邮箱是否被其他用户使用
        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new UserServiceException("邮箱已被其他用户使用");
            }
            user.setEmail(request.getEmail());
        }

        // 检查手机号是否被其他用户使用
        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new UserServiceException("手机号已被其他用户使用");
            }
            user.setPhone(request.getPhone());
        }

        // 更新用户画像数据
        if (request.getProfileData() != null) {
            try {
                String profileJson = objectMapper.writeValueAsString(request.getProfileData());
                user.setProfileDataJson(profileJson);
            } catch (JsonProcessingException e) {
                throw new UserServiceException("用户画像数据序列化失败", e);
            }
        }

        UserEntity updatedUser = userRepository.save(user);
        log.info("用户信息更新成功: {}", userId);

        return convertToUserResponse(updatedUser);
    }

    /**
     * 根据ID获取用户信息
     */
    public UserResponse getUserById(Long userId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new UserServiceException("用户不存在"));
        
        return convertToUserResponse(user);
    }

    /**
     * 根据用户名获取用户信息
     */
    public UserResponse getUserByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UserServiceException("用户不存在"));
        
        return convertToUserResponse(user);
    }

    /**
     * 获取所有激活用户
     */
    public List<UserResponse> getActiveUsers() {
        List<UserEntity> users = userRepository.findByStatus(1);
        return users.stream()
            .map(this::convertToUserResponse)
            .collect(Collectors.toList());
    }

    /**
     * 禁用用户
     */
    @Transactional
    public void disableUser(Long userId) {
        log.info("禁用用户: {}", userId);
        
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new UserServiceException("用户不存在"));
        
        user.setStatus(0);
        userRepository.save(user);
        
        log.info("用户禁用成功: {}", userId);
    }

    /**
     * 启用用户
     */
    @Transactional
    public void enableUser(Long userId) {
        log.info("启用用户: {}", userId);
        
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new UserServiceException("用户不存在"));
        
        user.setStatus(1);
        userRepository.save(user);
        
        log.info("用户启用成功: {}", userId);
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long userId) {
        log.info("删除用户: {}", userId);
        
        if (!userRepository.existsById(userId)) {
            throw new UserServiceException("用户不存在");
        }
        
        userRepository.deleteById(userId);
        log.info("用户删除成功: {}", userId);
    }

    /**
     * 转换UserEntity实体为UserResponse DTO
     */
    private UserResponse convertToUserResponse(UserEntity user) {
        Map<String, Object> profileData = null;
        if (user.getProfileDataJson() != null) {
            try {
                profileData = objectMapper.readValue(user.getProfileDataJson(), Map.class);
            } catch (JsonProcessingException e) {
                log.warn("用户画像数据反序列化失败: {}", user.getId(), e);
            }
        }

        return UserResponse.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .phone(user.getPhone())
            .profileData(profileData)
            .status(user.getStatus())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .build();
    }
}