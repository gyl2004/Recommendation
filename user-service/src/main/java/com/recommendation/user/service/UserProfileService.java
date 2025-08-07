package com.recommendation.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.user.entity.UserEntity;
import com.recommendation.user.entity.UserBehaviorEntity;
import com.recommendation.user.exception.UserServiceException;
import com.recommendation.user.repository.UserEntityRepository;
import com.recommendation.user.repository.UserBehaviorEntityRepository;
import com.recommendation.user.dto.UserProfileRequest;
import com.recommendation.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 用户画像构建服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserEntityRepository userRepository;
    private final UserBehaviorEntityRepository behaviorRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;

    // Redis缓存键前缀
    private static final String USER_PROFILE_CACHE_PREFIX = "user:profile:";
    private static final String USER_INTERESTS_CACHE_PREFIX = "user:interests:";
    private static final String USER_PREFERENCES_CACHE_PREFIX = "user:preferences:";

    // 缓存过期时间（小时）
    private static final long CACHE_EXPIRE_HOURS = 24;

    /**
     * 构建用户画像
     */
    @Transactional
    public UserProfileResponse buildUserProfile(Long userId) {
        log.info("开始构建用户画像: userId={}", userId);

        // 检查用户是否存在
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserServiceException("用户不存在"));

        // 获取用户行为数据
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<UserBehaviorEntity> recentBehaviors = behaviorRepository
                .findByUserIdAndTimestampBetween(userId, thirtyDaysAgo, LocalDateTime.now());

        // 计算兴趣标签权重
        Map<String, Double> interestWeights = calculateInterestWeights(recentBehaviors);

        // 计算内容类型偏好度
        Map<String, Double> contentTypePreferences = calculateContentTypePreferences(recentBehaviors);

        // 计算行为特征
        Map<String, Object> behaviorFeatures = calculateBehaviorFeatures(recentBehaviors);

        // 获取人口统计学特征
        Map<String, Object> demographicFeatures = extractDemographicFeatures(user);

        // 计算活跃度分数
        Double activityScore = calculateActivityScore(recentBehaviors);

        // 计算画像质量分数
        Double profileQualityScore = calculateProfileQualityScore(
                interestWeights, contentTypePreferences, behaviorFeatures, demographicFeatures);

        // 构建用户画像响应
        UserProfileResponse profile = UserProfileResponse.builder()
                .userId(userId)
                .interestWeights(interestWeights)
                .contentTypePreferences(contentTypePreferences)
                .behaviorFeatures(behaviorFeatures)
                .demographicFeatures(demographicFeatures)
                .activityScore(activityScore)
                .updatedAt(LocalDateTime.now())
                .profileQualityScore(profileQualityScore)
                .build();

        // 更新用户实体的画像数据
        updateUserProfileData(user, profile);

        // 缓存用户画像
        cacheUserProfile(userId, profile);

        // 发送画像更新消息
        sendProfileUpdateMessage(userId, profile);

        log.info("用户画像构建完成: userId={}, qualityScore={}", userId, profileQualityScore);
        return profile;
    }

    /**
     * 更新用户画像
     */
    @Transactional
    public UserProfileResponse updateUserProfile(Long userId, UserProfileRequest request) {
        log.info("更新用户画像: userId={}", userId);

        // 获取当前画像
        UserProfileResponse currentProfile = getUserProfile(userId);
        if (currentProfile == null) {
            // 如果没有现有画像，先构建一个
            currentProfile = buildUserProfile(userId);
        }

        // 合并更新数据
        if (request.getInterestWeights() != null) {
            Map<String, Double> mergedInterests = new HashMap<>(currentProfile.getInterestWeights());
            mergedInterests.putAll(request.getInterestWeights());
            currentProfile.setInterestWeights(mergedInterests);
        }

        if (request.getContentTypePreferences() != null) {
            Map<String, Double> mergedPreferences = new HashMap<>(currentProfile.getContentTypePreferences());
            mergedPreferences.putAll(request.getContentTypePreferences());
            currentProfile.setContentTypePreferences(mergedPreferences);
        }

        if (request.getBehaviorFeatures() != null) {
            Map<String, Object> mergedBehaviorFeatures = new HashMap<>(currentProfile.getBehaviorFeatures());
            mergedBehaviorFeatures.putAll(request.getBehaviorFeatures());
            currentProfile.setBehaviorFeatures(mergedBehaviorFeatures);
        }

        if (request.getDemographicFeatures() != null) {
            Map<String, Object> mergedDemographicFeatures = new HashMap<>(currentProfile.getDemographicFeatures());
            mergedDemographicFeatures.putAll(request.getDemographicFeatures());
            currentProfile.setDemographicFeatures(mergedDemographicFeatures);
        }

        // 重新计算画像质量分数
        Double newQualityScore = calculateProfileQualityScore(
                currentProfile.getInterestWeights(),
                currentProfile.getContentTypePreferences(),
                currentProfile.getBehaviorFeatures(),
                currentProfile.getDemographicFeatures());
        currentProfile.setProfileQualityScore(newQualityScore);
        currentProfile.setUpdatedAt(LocalDateTime.now());

        // 更新数据库
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserServiceException("用户不存在"));
        updateUserProfileData(user, currentProfile);

        // 更新缓存
        cacheUserProfile(userId, currentProfile);

        // 发送更新消息
        sendProfileUpdateMessage(userId, currentProfile);

        log.info("用户画像更新完成: userId={}, newQualityScore={}", userId, newQualityScore);
        return currentProfile;
    }

    /**
     * 获取用户画像
     */
    public UserProfileResponse getUserProfile(Long userId) {
        log.debug("获取用户画像: userId={}", userId);

        // 先从缓存获取
        String cacheKey = USER_PROFILE_CACHE_PREFIX + userId;
        UserProfileResponse cachedProfile = (UserProfileResponse) redisTemplate.opsForValue().get(cacheKey);
        if (cachedProfile != null) {
            log.debug("从缓存获取用户画像: userId={}", userId);
            return cachedProfile;
        }

        // 从数据库获取
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            return null;
        }

        UserEntity user = userOpt.get();
        if (user.getProfileDataJson() == null) {
            // 如果没有画像数据，构建一个新的
            return buildUserProfile(userId);
        }

        try {
            Map<String, Object> profileData = objectMapper.readValue(user.getProfileDataJson(), Map.class);
            UserProfileResponse profile = convertMapToProfile(userId, profileData);
            
            // 缓存结果
            cacheUserProfile(userId, profile);
            
            return profile;
        } catch (JsonProcessingException e) {
            log.error("用户画像数据反序列化失败: userId={}", userId, e);
            // 如果反序列化失败，重新构建画像
            return buildUserProfile(userId);
        }
    }

    /**
     * 计算兴趣标签权重
     */
    private Map<String, Double> calculateInterestWeights(List<UserBehaviorEntity> behaviors) {
        Map<String, Double> interestWeights = new HashMap<>();
        Map<String, Integer> actionCounts = new HashMap<>();

        // 统计各种行为的次数
        for (UserBehaviorEntity behavior : behaviors) {
            String contentType = behavior.getContentType();
            if (contentType != null) {
                actionCounts.merge(contentType, 1, Integer::sum);
            }
        }

        // 计算权重（基于行为频次和时间衰减）
        int totalActions = actionCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalActions > 0) {
            for (Map.Entry<String, Integer> entry : actionCounts.entrySet()) {
                double weight = (double) entry.getValue() / totalActions;
                interestWeights.put(entry.getKey(), weight);
            }
        }

        return interestWeights;
    }

    /**
     * 计算内容类型偏好度
     */
    private Map<String, Double> calculateContentTypePreferences(List<UserBehaviorEntity> behaviors) {
        Map<String, Double> preferences = new HashMap<>();
        Map<String, List<String>> contentTypeActions = new HashMap<>();

        // 按内容类型分组行为
        for (UserBehaviorEntity behavior : behaviors) {
            String contentType = behavior.getContentType();
            if (contentType != null) {
                contentTypeActions.computeIfAbsent(contentType, k -> new ArrayList<>())
                        .add(behavior.getActionType());
            }
        }

        // 计算偏好度（基于行为多样性和深度）
        for (Map.Entry<String, List<String>> entry : contentTypeActions.entrySet()) {
            String contentType = entry.getKey();
            List<String> actions = entry.getValue();
            
            // 行为多样性分数
            Set<String> uniqueActions = new HashSet<>(actions);
            double diversityScore = (double) uniqueActions.size() / 5.0; // 假设最多5种行为类型
            
            // 行为频次分数
            double frequencyScore = Math.min(1.0, actions.size() / 10.0); // 标准化到0-1
            
            // 综合偏好度
            double preference = (diversityScore * 0.4 + frequencyScore * 0.6);
            preferences.put(contentType, Math.min(1.0, preference));
        }

        return preferences;
    }

    /**
     * 计算行为特征
     */
    private Map<String, Object> calculateBehaviorFeatures(List<UserBehaviorEntity> behaviors) {
        Map<String, Object> features = new HashMap<>();

        if (behaviors.isEmpty()) {
            return features;
        }

        // 总行为次数
        features.put("totalBehaviors", behaviors.size());

        // 平均会话时长
        double avgDuration = behaviors.stream()
                .filter(b -> b.getDuration() != null)
                .mapToInt(UserBehaviorEntity::getDuration)
                .average()
                .orElse(0.0);
        features.put("avgSessionDuration", avgDuration);

        // 最活跃时间段
        Map<Integer, Long> hourCounts = behaviors.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getTimestamp().getHour(),
                        Collectors.counting()));
        Integer mostActiveHour = hourCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0);
        features.put("mostActiveHour", mostActiveHour);

        // 行为类型分布
        Map<String, Long> actionTypeCounts = behaviors.stream()
                .collect(Collectors.groupingBy(
                        UserBehaviorEntity::getActionType,
                        Collectors.counting()));
        features.put("actionTypeDistribution", actionTypeCounts);

        // 设备类型偏好
        Map<String, Long> deviceTypeCounts = behaviors.stream()
                .filter(b -> b.getDeviceType() != null)
                .collect(Collectors.groupingBy(
                        UserBehaviorEntity::getDeviceType,
                        Collectors.counting()));
        features.put("deviceTypeDistribution", deviceTypeCounts);

        return features;
    }

    /**
     * 提取人口统计学特征
     */
    private Map<String, Object> extractDemographicFeatures(UserEntity user) {
        Map<String, Object> features = new HashMap<>();

        // 从用户基础信息中提取特征
        if (user.getProfileDataJson() != null) {
            try {
                Map<String, Object> profileData = objectMapper.readValue(user.getProfileDataJson(), Map.class);
                
                // 提取年龄、性别、地区等信息
                if (profileData.containsKey("age")) {
                    features.put("age", profileData.get("age"));
                }
                if (profileData.containsKey("gender")) {
                    features.put("gender", profileData.get("gender"));
                }
                if (profileData.containsKey("city")) {
                    features.put("city", profileData.get("city"));
                }
                if (profileData.containsKey("education")) {
                    features.put("education", profileData.get("education"));
                }
                
            } catch (JsonProcessingException e) {
                log.warn("提取人口统计学特征失败: userId={}", user.getId(), e);
            }
        }

        return features;
    }

    /**
     * 计算活跃度分数
     */
    private Double calculateActivityScore(List<UserBehaviorEntity> behaviors) {
        if (behaviors.isEmpty()) {
            return 0.0;
        }

        // 基于行为频次和时间分布计算活跃度
        LocalDateTime now = LocalDateTime.now();
        double totalScore = 0.0;

        for (UserBehaviorEntity behavior : behaviors) {
            // 时间衰减因子
            long daysAgo = java.time.Duration.between(behavior.getTimestamp(), now).toDays();
            double timeDecay = Math.exp(-daysAgo / 7.0); // 7天半衰期

            // 行为权重
            double actionWeight = getActionWeight(behavior.getActionType());

            totalScore += actionWeight * timeDecay;
        }

        // 标准化到0-1范围
        return Math.min(1.0, totalScore / 100.0);
    }

    /**
     * 获取行为权重
     */
    private double getActionWeight(String actionType) {
        switch (actionType.toLowerCase()) {
            case "view": return 1.0;
            case "click": return 2.0;
            case "like": return 5.0;
            case "share": return 10.0;
            case "comment": return 8.0;
            default: return 1.0;
        }
    }

    /**
     * 计算画像质量分数
     */
    private Double calculateProfileQualityScore(Map<String, Double> interestWeights,
                                              Map<String, Double> contentTypePreferences,
                                              Map<String, Object> behaviorFeatures,
                                              Map<String, Object> demographicFeatures) {
        double score = 0.0;

        // 兴趣标签完整性 (25%)
        if (!interestWeights.isEmpty()) {
            score += 0.25 * Math.min(1.0, interestWeights.size() / 5.0);
        }

        // 内容偏好完整性 (25%)
        if (!contentTypePreferences.isEmpty()) {
            score += 0.25 * Math.min(1.0, contentTypePreferences.size() / 3.0);
        }

        // 行为特征丰富度 (25%)
        if (!behaviorFeatures.isEmpty()) {
            score += 0.25 * Math.min(1.0, behaviorFeatures.size() / 6.0);
        }

        // 人口统计学特征完整性 (25%)
        if (!demographicFeatures.isEmpty()) {
            score += 0.25 * Math.min(1.0, demographicFeatures.size() / 4.0);
        }

        return score;
    }

    /**
     * 更新用户画像数据到数据库
     */
    private void updateUserProfileData(UserEntity user, UserProfileResponse profile) {
        try {
            Map<String, Object> profileData = new HashMap<>();
            profileData.put("interestWeights", profile.getInterestWeights());
            profileData.put("contentTypePreferences", profile.getContentTypePreferences());
            profileData.put("behaviorFeatures", profile.getBehaviorFeatures());
            profileData.put("demographicFeatures", profile.getDemographicFeatures());
            profileData.put("activityScore", profile.getActivityScore());
            profileData.put("profileQualityScore", profile.getProfileQualityScore());
            profileData.put("updatedAt", profile.getUpdatedAt().toString());

            String profileJson = objectMapper.writeValueAsString(profileData);
            user.setProfileDataJson(profileJson);
            userRepository.save(user);

        } catch (JsonProcessingException e) {
            log.error("更新用户画像数据失败: userId={}", user.getId(), e);
            throw new UserServiceException("更新用户画像数据失败", e);
        }
    }

    /**
     * 缓存用户画像
     */
    private void cacheUserProfile(Long userId, UserProfileResponse profile) {
        try {
            String cacheKey = USER_PROFILE_CACHE_PREFIX + userId;
            redisTemplate.opsForValue().set(cacheKey, profile, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

            // 单独缓存兴趣标签和偏好，便于快速访问
            String interestsCacheKey = USER_INTERESTS_CACHE_PREFIX + userId;
            redisTemplate.opsForValue().set(interestsCacheKey, profile.getInterestWeights(), 
                    CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

            String preferencesCacheKey = USER_PREFERENCES_CACHE_PREFIX + userId;
            redisTemplate.opsForValue().set(preferencesCacheKey, profile.getContentTypePreferences(), 
                    CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

            log.debug("用户画像已缓存: userId={}", userId);

        } catch (Exception e) {
            log.error("缓存用户画像失败: userId={}", userId, e);
        }
    }

    /**
     * 发送画像更新消息
     */
    private void sendProfileUpdateMessage(Long userId, UserProfileResponse profile) {
        try {
            Map<String, Object> message = Map.of(
                "userId", userId,
                "activityScore", profile.getActivityScore(),
                "profileQualityScore", profile.getProfileQualityScore(),
                "updatedAt", profile.getUpdatedAt().toString()
            );

            rabbitTemplate.convertAndSend("user.profile.exchange", "user.profile.routing.key", message);
            log.debug("用户画像更新消息已发送: userId={}", userId);

        } catch (Exception e) {
            log.error("发送用户画像更新消息失败: userId={}", userId, e);
        }
    }

    /**
     * 将Map转换为UserProfileResponse
     */
    private UserProfileResponse convertMapToProfile(Long userId, Map<String, Object> profileData) {
        return UserProfileResponse.builder()
                .userId(userId)
                .interestWeights((Map<String, Double>) profileData.get("interestWeights"))
                .contentTypePreferences((Map<String, Double>) profileData.get("contentTypePreferences"))
                .behaviorFeatures((Map<String, Object>) profileData.get("behaviorFeatures"))
                .demographicFeatures((Map<String, Object>) profileData.get("demographicFeatures"))
                .activityScore((Double) profileData.get("activityScore"))
                .profileQualityScore((Double) profileData.get("profileQualityScore"))
                .updatedAt(LocalDateTime.parse((String) profileData.get("updatedAt")))
                .build();
    }
}