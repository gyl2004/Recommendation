package com.recommendation.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.service.dto.FeedbackRequest;
import com.recommendation.service.dto.RecommendResponse;
import com.recommendation.service.service.FeedbackService;
import com.recommendation.service.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 推荐控制器测试
 */
@WebMvcTest(RecommendController.class)
class RecommendControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private RecommendationService recommendationService;
    
    @MockBean
    private FeedbackService feedbackService;
    
    @Test
    void testRecommendContent() throws Exception {
        // 准备测试数据
        RecommendResponse.RecommendItem item = RecommendResponse.RecommendItem.builder()
                .contentId("test_content_1")
                .contentType("article")
                .title("测试文章标题")
                .description("测试文章描述")
                .score(85.0)
                .reason("基于您的兴趣推荐")
                .confidence(0.8)
                .tags(Arrays.asList("科技", "AI"))
                .build();
        
        RecommendResponse response = RecommendResponse.builder()
                .items(Arrays.asList(item))
                .total(1)
                .requestId("test_request_id")
                .algorithmVersion("v1.0")
                .fromCache(false)
                .extraInfo(new HashMap<>())
                .build();
        
        when(recommendationService.recommend(any(), any())).thenReturn(response);
        
        // 执行测试
        mockMvc.perform(get("/recommend/content")
                .param("userId", "test_user_1")
                .param("size", "10")
                .param("contentType", "article"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items[0].contentId").value("test_content_1"))
                .andExpect(jsonPath("$.items[0].title").value("测试文章标题"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.fromCache").value(false));
    }
    
    @Test
    void testRecordFeedback() throws Exception {
        // 准备测试数据
        FeedbackRequest request = FeedbackRequest.builder()
                .userId("test_user_1")
                .contentId("test_content_1")
                .feedbackType("click")
                .sessionId("test_session_1")
                .duration(5000L)
                .position(1)
                .timestamp(System.currentTimeMillis())
                .build();
        
        // 执行测试
        mockMvc.perform(post("/recommend/feedback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
    
    @Test
    void testRecommendContentWithInvalidParams() throws Exception {
        // 测试无效参数
        mockMvc.perform(get("/recommend/content")
                .param("userId", "")
                .param("size", "10"))
                .andExpect(status().isBadRequest());
        
        mockMvc.perform(get("/recommend/content")
                .param("userId", "test_user_1")
                .param("size", "0"))
                .andExpect(status().isBadRequest());
        
        mockMvc.perform(get("/recommend/content")
                .param("userId", "test_user_1")
                .param("contentType", "invalid_type"))
                .andExpect(status().isBadRequest());
    }
    
    @Test
    void testExplainRecommendation() throws Exception {
        when(recommendationService.explainRecommendation(anyString(), anyString(), anyString()))
                .thenReturn("基于您的兴趣偏好为您推荐");
        
        mockMvc.perform(get("/recommend/explain")
                .param("userId", "test_user_1")
                .param("contentId", "test_content_1"))
                .andExpect(status().isOk())
                .andExpect(content().string("基于您的兴趣偏好为您推荐"));
    }
}