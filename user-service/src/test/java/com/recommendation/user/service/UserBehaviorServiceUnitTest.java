package com.recommendation.user.service;

import com.recommendation.user.dto.UserBehaviorRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户行为服务单元测试 - 不依赖Spring容器
 */
class UserBehaviorServiceUnitTest {

    private UserBehaviorRequest behaviorRequest;

    @BeforeEach
    void setUp() {
        behaviorRequest = new UserBehaviorRequest();
        behaviorRequest.setUserId(1L);
        behaviorRequest.setContentId(100L);
        behaviorRequest.setActionType("view");
        behaviorRequest.setContentType("article");
        behaviorRequest.setSessionId("session123");
        behaviorRequest.setDeviceType("mobile");
        behaviorRequest.setDuration(30);

        Map<String, Object> extraData = new HashMap<>();
        extraData.put("source", "homepage");
        extraData.put("position", 1);
        behaviorRequest.setExtraData(extraData);
    }

    @Test
    void testUserBehaviorRequestValidation() {
        // 测试用户行为请求DTO的基本功能
        assertEquals(1L, behaviorRequest.getUserId());
        assertEquals(100L, behaviorRequest.getContentId());
        assertEquals("view", behaviorRequest.getActionType());
        assertEquals("article", behaviorRequest.getContentType());
        assertEquals("session123", behaviorRequest.getSessionId());
        assertEquals("mobile", behaviorRequest.getDeviceType());
        assertEquals(30, behaviorRequest.getDuration());
        assertNotNull(behaviorRequest.getExtraData());
        assertEquals("homepage", behaviorRequest.getExtraData().get("source"));
        assertEquals(1, behaviorRequest.getExtraData().get("position"));
    }

    @Test
    void testUserBehaviorRequestWithMinimalData() {
        // 测试最小必需数据的用户行为请求
        UserBehaviorRequest minimalRequest = new UserBehaviorRequest();
        minimalRequest.setUserId(2L);
        minimalRequest.setContentId(200L);
        minimalRequest.setActionType("click");

        assertEquals(2L, minimalRequest.getUserId());
        assertEquals(200L, minimalRequest.getContentId());
        assertEquals("click", minimalRequest.getActionType());
        assertNull(minimalRequest.getContentType());
        assertNull(minimalRequest.getSessionId());
        assertNull(minimalRequest.getDeviceType());
        assertNull(minimalRequest.getDuration());
        assertNull(minimalRequest.getExtraData());
    }

    @Test
    void testDifferentActionTypes() {
        // 测试不同的行为类型
        String[] actionTypes = {"view", "click", "like", "share", "comment"};
        
        for (String actionType : actionTypes) {
            UserBehaviorRequest request = new UserBehaviorRequest();
            request.setUserId(1L);
            request.setContentId(100L);
            request.setActionType(actionType);
            
            assertEquals(actionType, request.getActionType());
        }
    }

    @Test
    void testDifferentContentTypes() {
        // 测试不同的内容类型
        String[] contentTypes = {"article", "video", "product"};
        
        for (String contentType : contentTypes) {
            UserBehaviorRequest request = new UserBehaviorRequest();
            request.setUserId(1L);
            request.setContentId(100L);
            request.setActionType("view");
            request.setContentType(contentType);
            
            assertEquals(contentType, request.getContentType());
        }
    }

    @Test
    void testDifferentDeviceTypes() {
        // 测试不同的设备类型
        String[] deviceTypes = {"mobile", "desktop", "tablet"};
        
        for (String deviceType : deviceTypes) {
            UserBehaviorRequest request = new UserBehaviorRequest();
            request.setUserId(1L);
            request.setContentId(100L);
            request.setActionType("view");
            request.setDeviceType(deviceType);
            
            assertEquals(deviceType, request.getDeviceType());
        }
    }
}