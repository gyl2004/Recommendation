package com.recommendation.user.service;

import com.recommendation.user.dto.UserProfileRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户画像服务单元测试 - 不依赖Spring容器
 */
class UserProfileServiceUnitTest {

    private UserProfileRequest profileRequest;

    @BeforeEach
    void setUp() {
        profileRequest = new UserProfileRequest();
        profileRequest.setUserId(1L);

        // 设置兴趣标签权重
        Map<String, Double> interestWeights = new HashMap<>();
        interestWeights.put("technology", 0.8);
        interestWeights.put("sports", 0.6);
        interestWeights.put("travel", 0.4);
        profileRequest.setInterestWeights(interestWeights);

        // 设置内容类型偏好度
        Map<String, Double> contentTypePreferences = new HashMap<>();
        contentTypePreferences.put("article", 0.7);
        contentTypePreferences.put("video", 0.9);
        contentTypePreferences.put("product", 0.3);
        profileRequest.setContentTypePreferences(contentTypePreferences);

        // 设置行为特征
        Map<String, Object> behaviorFeatures = new HashMap<>();
        behaviorFeatures.put("avgSessionDuration", 120.5);
        behaviorFeatures.put("mostActiveHour", 20);
        behaviorFeatures.put("totalBehaviors", 150);
        profileRequest.setBehaviorFeatures(behaviorFeatures);

        // 设置人口统计学特征
        Map<String, Object> demographicFeatures = new HashMap<>();
        demographicFeatures.put("age", 28);
        demographicFeatures.put("gender", "male");
        demographicFeatures.put("city", "Beijing");
        demographicFeatures.put("education", "bachelor");
        profileRequest.setDemographicFeatures(demographicFeatures);
    }

    @Test
    void testUserProfileRequestValidation() {
        // 测试用户画像请求DTO的基本功能
        assertEquals(1L, profileRequest.getUserId());
        assertNotNull(profileRequest.getInterestWeights());
        assertNotNull(profileRequest.getContentTypePreferences());
        assertNotNull(profileRequest.getBehaviorFeatures());
        assertNotNull(profileRequest.getDemographicFeatures());

        // 验证兴趣标签权重
        assertEquals(0.8, profileRequest.getInterestWeights().get("technology"));
        assertEquals(0.6, profileRequest.getInterestWeights().get("sports"));
        assertEquals(0.4, profileRequest.getInterestWeights().get("travel"));

        // 验证内容类型偏好度
        assertEquals(0.7, profileRequest.getContentTypePreferences().get("article"));
        assertEquals(0.9, profileRequest.getContentTypePreferences().get("video"));
        assertEquals(0.3, profileRequest.getContentTypePreferences().get("product"));

        // 验证行为特征
        assertEquals(120.5, profileRequest.getBehaviorFeatures().get("avgSessionDuration"));
        assertEquals(20, profileRequest.getBehaviorFeatures().get("mostActiveHour"));
        assertEquals(150, profileRequest.getBehaviorFeatures().get("totalBehaviors"));

        // 验证人口统计学特征
        assertEquals(28, profileRequest.getDemographicFeatures().get("age"));
        assertEquals("male", profileRequest.getDemographicFeatures().get("gender"));
        assertEquals("Beijing", profileRequest.getDemographicFeatures().get("city"));
        assertEquals("bachelor", profileRequest.getDemographicFeatures().get("education"));
    }

    @Test
    void testUserProfileRequestWithMinimalData() {
        // 测试最小必需数据的用户画像请求
        UserProfileRequest minimalRequest = new UserProfileRequest();
        minimalRequest.setUserId(2L);

        assertEquals(2L, minimalRequest.getUserId());
        assertNull(minimalRequest.getInterestWeights());
        assertNull(minimalRequest.getContentTypePreferences());
        assertNull(minimalRequest.getBehaviorFeatures());
        assertNull(minimalRequest.getDemographicFeatures());
    }

    @Test
    void testInterestWeightsValidation() {
        // 测试兴趣标签权重的有效性
        Map<String, Double> interestWeights = profileRequest.getInterestWeights();
        
        // 验证权重值在合理范围内
        for (Double weight : interestWeights.values()) {
            assertTrue(weight >= 0.0 && weight <= 1.0, "兴趣权重应该在0.0-1.0范围内");
        }

        // 验证标签名称不为空
        for (String tag : interestWeights.keySet()) {
            assertNotNull(tag);
            assertFalse(tag.trim().isEmpty());
        }
    }

    @Test
    void testContentTypePreferencesValidation() {
        // 测试内容类型偏好度的有效性
        Map<String, Double> preferences = profileRequest.getContentTypePreferences();
        
        // 验证偏好度值在合理范围内
        for (Double preference : preferences.values()) {
            assertTrue(preference >= 0.0 && preference <= 1.0, "内容偏好度应该在0.0-1.0范围内");
        }

        // 验证内容类型名称不为空
        for (String contentType : preferences.keySet()) {
            assertNotNull(contentType);
            assertFalse(contentType.trim().isEmpty());
        }
    }

    @Test
    void testBehaviorFeaturesValidation() {
        // 测试行为特征的有效性
        Map<String, Object> behaviorFeatures = profileRequest.getBehaviorFeatures();
        
        // 验证平均会话时长
        Object avgSessionDuration = behaviorFeatures.get("avgSessionDuration");
        assertNotNull(avgSessionDuration);
        assertTrue(avgSessionDuration instanceof Number);
        assertTrue(((Number) avgSessionDuration).doubleValue() >= 0);

        // 验证最活跃时间
        Object mostActiveHour = behaviorFeatures.get("mostActiveHour");
        assertNotNull(mostActiveHour);
        assertTrue(mostActiveHour instanceof Number);
        int hour = ((Number) mostActiveHour).intValue();
        assertTrue(hour >= 0 && hour <= 23, "最活跃时间应该在0-23范围内");

        // 验证总行为数
        Object totalBehaviors = behaviorFeatures.get("totalBehaviors");
        assertNotNull(totalBehaviors);
        assertTrue(totalBehaviors instanceof Number);
        assertTrue(((Number) totalBehaviors).intValue() >= 0);
    }

    @Test
    void testDemographicFeaturesValidation() {
        // 测试人口统计学特征的有效性
        Map<String, Object> demographicFeatures = profileRequest.getDemographicFeatures();
        
        // 验证年龄
        Object age = demographicFeatures.get("age");
        assertNotNull(age);
        assertTrue(age instanceof Number);
        int ageValue = ((Number) age).intValue();
        assertTrue(ageValue > 0 && ageValue < 150, "年龄应该在合理范围内");

        // 验证性别
        Object gender = demographicFeatures.get("gender");
        assertNotNull(gender);
        assertTrue(gender instanceof String);
        assertFalse(((String) gender).trim().isEmpty());

        // 验证城市
        Object city = demographicFeatures.get("city");
        assertNotNull(city);
        assertTrue(city instanceof String);
        assertFalse(((String) city).trim().isEmpty());

        // 验证教育程度
        Object education = demographicFeatures.get("education");
        assertNotNull(education);
        assertTrue(education instanceof String);
        assertFalse(((String) education).trim().isEmpty());
    }
}