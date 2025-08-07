package com.recommendation.content.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.content.dto.ContentFeatures;
import com.recommendation.content.entity.ContentEntity;
import com.recommendation.content.repository.ContentEntityRepository;
import com.recommendation.content.service.ContentFeatureExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 内容特征提取控制器集成测试
 * 测试需求6.1, 6.2, 6.3: 特征工程处理API
 */
@WebMvcTest(ContentFeatureController.class)
class ContentFeatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContentFeatureExtractionService featureExtractionService;

    @MockBean
    private ContentEntityRepository contentRepository;

    private ContentEntity testContent;
    private ContentFeatures testFeatures;

    @BeforeEach
    void setUp() {
        // 创建测试内容
        testContent = new ContentEntity();
        testContent.setId(1L);
        testContent.setTitle("测试内容");
        testContent.setContentType(ContentEntity.ContentType.ARTICLE);
        testContent.setContentData("{\"content\":\"测试文章内容\"}");
        testContent.setTags("[\"测试\",\"文章\"]");
        testContent.setCategoryId(1);
        testContent.setAuthorId(100L);
        testContent.setViewCount(1000L);
        testContent.setLikeCount(50L);
        testContent.setShareCount(10L);
        testContent.setCommentCount(20L);
        testContent.setHotScore(BigDecimal.valueOf(85.5));
        testContent.setPublishTime(LocalDateTime.now().minusHours(2));

        // 创建测试特征
        testFeatures = ContentFeatures.builder()
                .contentId(1L)
                .contentType("article")
                .textFeatures(ContentFeatures.TextFeatures.builder()
                        .tfidfVector(new HashMap<>())
                        .wordEmbedding(Arrays.asList(0.1, 0.2, 0.3))
                        .keywords(Arrays.asList("测试", "文章"))
                        .textLength(100)
                        .sentenceCount(5)
                        .paragraphCount(2)
                        .sentimentScore(0.5)
                        .build())
                .commonFeatures(ContentFeatures.CommonFeatures.builder()
                        .tags(Arrays.asList("测试", "文章"))
                        .categoryId(1)
                        .hotScore(85.5)
                        .authorId(100L)
                        .viewCount(1000L)
                        .likeCount(50L)
                        .shareCount(10L)
                        .commentCount(20L)
                        .engagementRate(0.08)
                        .freshnessHours(2)
                        .build())
                .build();
    }

    @Test
    void testExtractContentFeatures_Success() throws Exception {
        // Mock repository and service
        when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
        when(featureExtractionService.extractFeatures(any(ContentEntity.class))).thenReturn(testFeatures);

        // 执行请求
        mockMvc.perform(get("/api/v1/content/features/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.contentId").value(1))
                .andExpect(jsonPath("$.data.contentType").value("article"))
                .andExpect(jsonPath("$.data.textFeatures.textLength").value(100))
                .andExpect(jsonPath("$.data.textFeatures.sentenceCount").value(5))
                .andExpect(jsonPath("$.data.textFeatures.sentimentScore").value(0.5))
                .andExpect(jsonPath("$.data.commonFeatures.categoryId").value(1))
                .andExpect(jsonPath("$.data.commonFeatures.hotScore").value(85.5))
                .andExpect(jsonPath("$.data.commonFeatures.engagementRate").value(0.08));
    }

    @Test
    void testExtractContentFeatures_ContentNotFound() throws Exception {
        // Mock repository to return empty
        when(contentRepository.findById(999L)).thenReturn(Optional.empty());

        // 执行请求
        mockMvc.perform(get("/api/v1/content/features/999")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("内容特征提取失败: 内容不存在: 999"));
    }

    @Test
    void testExtractTextFeatures_Success() throws Exception {
        // Mock repository and service
        when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
        when(featureExtractionService.extractTextFeatures(any(ContentEntity.class), any(String.class)))
                .thenReturn(testFeatures.getTextFeatures());

        // 执行请求
        mockMvc.perform(get("/api/v1/content/features/1/text")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.textLength").value(100))
                .andExpect(jsonPath("$.data.sentenceCount").value(5))
                .andExpect(jsonPath("$.data.paragraphCount").value(2))
                .andExpect(jsonPath("$.data.sentimentScore").value(0.5))
                .andExpect(jsonPath("$.data.keywords[0]").value("测试"))
                .andExpect(jsonPath("$.data.keywords[1]").value("文章"));
    }

    @Test
    void testExtractVideoFeatures_Success() throws Exception {
        // 创建视频内容
        ContentEntity videoContent = new ContentEntity();
        videoContent.setId(2L);
        videoContent.setContentType(ContentEntity.ContentType.VIDEO);
        videoContent.setContentData("{\"duration\":1800,\"width\":1920,\"height\":1080}");

        ContentFeatures.VideoFeatures videoFeatures = ContentFeatures.VideoFeatures.builder()
                .duration(1800)
                .width(1920)
                .height(1080)
                .format("mp4")
                .frameRate(30.0)
                .bitRate(2000)
                .hasAudio(true)
                .qualityScore(0.8)
                .durationCategory("long")
                .build();

        // Mock repository and service
        when(contentRepository.findById(2L)).thenReturn(Optional.of(videoContent));
        when(featureExtractionService.extractVideoFeatures(any(ContentEntity.class))).thenReturn(videoFeatures);

        // 执行请求
        mockMvc.perform(get("/api/v1/content/features/2/video")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.duration").value(1800))
                .andExpect(jsonPath("$.data.width").value(1920))
                .andExpect(jsonPath("$.data.height").value(1080))
                .andExpect(jsonPath("$.data.format").value("mp4"))
                .andExpect(jsonPath("$.data.frameRate").value(30.0))
                .andExpect(jsonPath("$.data.qualityScore").value(0.8))
                .andExpect(jsonPath("$.data.durationCategory").value("long"));
    }

    @Test
    void testExtractVideoFeatures_WrongContentType() throws Exception {
        // Mock repository to return article content for video feature request
        when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));

        // 执行请求
        mockMvc.perform(get("/api/v1/content/features/1/video")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("视频特征提取失败: 内容类型不是视频: ARTICLE"));
    }

    @Test
    void testExtractProductFeatures_Success() throws Exception {
        // 创建商品内容
        ContentEntity productContent = new ContentEntity();
        productContent.setId(3L);
        productContent.setContentType(ContentEntity.ContentType.PRODUCT);
        productContent.setContentData("{\"price\":999.0,\"brand\":\"TestBrand\"}");

        ContentFeatures.ProductFeatures productFeatures = ContentFeatures.ProductFeatures.builder()
                .price(999.0)
                .priceRange("medium")
                .brand("TestBrand")
                .attributeVector(new HashMap<>())
                .imageCount(3)
                .descriptionLength(50)
                .rating(4.5)
                .salesCount(100)
                .stockStatus("in_stock")
                .productStatus("normal")
                .build();

        // Mock repository and service
        when(contentRepository.findById(3L)).thenReturn(Optional.of(productContent));
        when(featureExtractionService.extractProductFeatures(any(ContentEntity.class))).thenReturn(productFeatures);

        // 执行请求
        mockMvc.perform(get("/api/v1/content/features/3/product")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.price").value(999.0))
                .andExpect(jsonPath("$.data.priceRange").value("medium"))
                .andExpect(jsonPath("$.data.brand").value("TestBrand"))
                .andExpect(jsonPath("$.data.imageCount").value(3))
                .andExpect(jsonPath("$.data.rating").value(4.5))
                .andExpect(jsonPath("$.data.stockStatus").value("in_stock"))
                .andExpect(jsonPath("$.data.productStatus").value("normal"));
    }

    @Test
    void testExtractProductFeatures_WrongContentType() throws Exception {
        // Mock repository to return article content for product feature request
        when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));

        // 执行请求
        mockMvc.perform(get("/api/v1/content/features/1/product")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("商品特征提取失败: 内容类型不是商品: ARTICLE"));
    }

    @Test
    void testExtractCommonFeatures_Success() throws Exception {
        // Mock repository and service
        when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
        when(featureExtractionService.extractCommonFeatures(any(ContentEntity.class)))
                .thenReturn(testFeatures.getCommonFeatures());

        // 执行请求
        mockMvc.perform(get("/api/v1/content/features/1/common")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.categoryId").value(1))
                .andExpect(jsonPath("$.data.authorId").value(100))
                .andExpect(jsonPath("$.data.viewCount").value(1000))
                .andExpect(jsonPath("$.data.likeCount").value(50))
                .andExpect(jsonPath("$.data.shareCount").value(10))
                .andExpect(jsonPath("$.data.commentCount").value(20))
                .andExpect(jsonPath("$.data.engagementRate").value(0.08))
                .andExpect(jsonPath("$.data.freshnessHours").value(2))
                .andExpect(jsonPath("$.data.tags[0]").value("测试"))
                .andExpect(jsonPath("$.data.tags[1]").value("文章"));
    }

    @Test
    void testBatchExtractFeatures_Success() throws Exception {
        // 创建多个内容
        ContentEntity content1 = new ContentEntity();
        content1.setId(1L);
        content1.setContentType(ContentEntity.ContentType.ARTICLE);

        ContentEntity content2 = new ContentEntity();
        content2.setId(2L);
        content2.setContentType(ContentEntity.ContentType.VIDEO);

        ContentFeatures features1 = ContentFeatures.builder()
                .contentId(1L)
                .contentType("article")
                .build();

        ContentFeatures features2 = ContentFeatures.builder()
                .contentId(2L)
                .contentType("video")
                .build();

        // Mock repository and service
        when(contentRepository.findById(1L)).thenReturn(Optional.of(content1));
        when(contentRepository.findById(2L)).thenReturn(Optional.of(content2));
        when(featureExtractionService.extractFeatures(content1)).thenReturn(features1);
        when(featureExtractionService.extractFeatures(content2)).thenReturn(features2);

        List<Long> contentIds = Arrays.asList(1L, 2L);

        // 执行请求
        mockMvc.perform(post("/api/v1/content/features/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contentIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.1.contentId").value(1))
                .andExpect(jsonPath("$.data.1.contentType").value("article"))
                .andExpect(jsonPath("$.data.2.contentId").value(2))
                .andExpect(jsonPath("$.data.2.contentType").value("video"));
    }

    @Test
    void testBatchExtractFeatures_PartialSuccess() throws Exception {
        // Mock repository - 第一个内容存在，第二个不存在
        ContentEntity content1 = new ContentEntity();
        content1.setId(1L);
        content1.setContentType(ContentEntity.ContentType.ARTICLE);

        ContentFeatures features1 = ContentFeatures.builder()
                .contentId(1L)
                .contentType("article")
                .build();

        when(contentRepository.findById(1L)).thenReturn(Optional.of(content1));
        when(contentRepository.findById(999L)).thenReturn(Optional.empty());
        when(featureExtractionService.extractFeatures(content1)).thenReturn(features1);

        List<Long> contentIds = Arrays.asList(1L, 999L);

        // 执行请求
        mockMvc.perform(post("/api/v1/content/features/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contentIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.1.contentId").value(1))
                .andExpect(jsonPath("$.data.1.contentType").value("article"))
                .andExpect(jsonPath("$.data.999").doesNotExist());
    }

    @Test
    void testBatchExtractFeatures_EmptyList() throws Exception {
        List<Long> contentIds = Arrays.asList();

        // 执行请求
        mockMvc.perform(post("/api/v1/content/features/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(contentIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void testExtractFeatures_ServiceException() throws Exception {
        // Mock repository and service to throw exception
        when(contentRepository.findById(1L)).thenReturn(Optional.of(testContent));
        when(featureExtractionService.extractFeatures(any(ContentEntity.class)))
                .thenThrow(new RuntimeException("特征提取服务异常"));

        // 执行请求
        mockMvc.perform(get("/api/v1/content/features/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("内容特征提取失败: 特征提取服务异常"));
    }
}