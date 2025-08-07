package com.recommendation.content.service;

import com.recommendation.content.entity.ContentEntity;
import com.recommendation.content.repository.ContentEntityRepository;
import com.recommendation.content.dto.ContentCreateRequest;
import com.recommendation.content.dto.ContentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 内容管理服务简单测试
 */
@ExtendWith(MockitoExtension.class)
class ContentManagementServiceSimpleTest {

    @Mock
    private ContentEntityRepository contentRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ContentManagementService contentManagementService;

    private ContentCreateRequest createRequest;
    private ContentEntity content;

    @BeforeEach
    void setUp() {
        // 创建请求
        createRequest = new ContentCreateRequest();
        createRequest.setTitle("测试文章");
        createRequest.setContentType(ContentEntity.ContentType.ARTICLE);
        createRequest.setStatus(ContentEntity.ContentStatus.PUBLISHED);

        Map<String, Object> contentData = new HashMap<>();
        contentData.put("content", "这是一篇测试文章的内容");
        contentData.put("summary", "测试摘要");
        createRequest.setContentData(contentData);

        createRequest.setTags(List.of("测试", "文章"));

        // 内容实体
        content = new ContentEntity();
        content.setId(1L);
        content.setTitle("测试文章");
        content.setContentType(ContentEntity.ContentType.ARTICLE);
        content.setContentData("{\"content\":\"这是一篇测试文章的内容\",\"summary\":\"测试摘要\"}");
        content.setTags("[\"测试\",\"文章\"]");
        content.setStatus(ContentEntity.ContentStatus.PUBLISHED);
        content.setPublishTime(LocalDateTime.now());
        content.setCreatedAt(LocalDateTime.now());
        content.setUpdatedAt(LocalDateTime.now());
    }

    @Test
    void testCreateContent_Success() throws Exception {
        // 准备mock
        when(objectMapper.writeValueAsString(any(Map.class))).thenReturn("{\"content\":\"这是一篇测试文章的内容\",\"summary\":\"测试摘要\"}");
        when(objectMapper.writeValueAsString(any(List.class))).thenReturn("[\"测试\",\"文章\"]");
        when(contentRepository.save(any(ContentEntity.class))).thenReturn(content);
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(createRequest.getContentData());
        when(objectMapper.readValue(anyString(), eq(List.class))).thenReturn(createRequest.getTags());

        // 执行测试
        ContentResponse response = contentManagementService.createContent(createRequest);

        // 验证结果
        assertNotNull(response);
        assertEquals("测试文章", response.getTitle());
        assertEquals(ContentEntity.ContentType.ARTICLE, response.getContentType());
        assertEquals(ContentEntity.ContentStatus.PUBLISHED, response.getStatus());

        // 验证mock调用
        verify(contentRepository).save(any(ContentEntity.class));
        verify(objectMapper, times(2)).writeValueAsString(any());
    }

    @Test
    void testIncrementViewCount() {
        // 准备mock
        when(contentRepository.incrementViewCount(1L)).thenReturn(1);

        // 执行测试
        contentManagementService.incrementViewCount(1L);

        // 验证mock调用
        verify(contentRepository).incrementViewCount(1L);
    }

    @Test
    void testIncrementLikeCount() {
        // 准备mock
        when(contentRepository.incrementLikeCount(1L)).thenReturn(1);

        // 执行测试
        contentManagementService.incrementLikeCount(1L);

        // 验证mock调用
        verify(contentRepository).incrementLikeCount(1L);
    }

    @Test
    void testIncrementShareCount() {
        // 准备mock
        when(contentRepository.incrementShareCount(1L)).thenReturn(1);

        // 执行测试
        contentManagementService.incrementShareCount(1L);

        // 验证mock调用
        verify(contentRepository).incrementShareCount(1L);
    }
}