package com.recommendation.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.content.document.ContentDocument;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Elasticsearch服务测试类
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
@ActiveProfiles("test")
class ElasticsearchServiceTest {

    @Mock
    private RestHighLevelClient elasticsearchClient;

    private ElasticsearchService elasticsearchService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        elasticsearchService = new ElasticsearchService(elasticsearchClient, objectMapper);
    }

    @Test
    void testCreateContentDocument() {
        // 创建测试文档
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("content", "这是一篇测试文章的内容");
        extraData.put("summary", "测试文章摘要");

        ContentDocument document = ContentDocument.builder()
                .contentId(1L)
                .title("测试文章标题")
                .contentType("article")
                .tags(Arrays.asList("技术", "Java", "Elasticsearch"))
                .category("技术分类")
                .publishTime(LocalDateTime.now())
                .hotScore(85.5f)
                .authorId(100L)
                .summary("这是一篇关于Elasticsearch的测试文章")
                .extraData(extraData)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 验证文档创建
        assertNotNull(document);
        assertEquals(1L, document.getContentId());
        assertEquals("测试文章标题", document.getTitle());
        assertEquals("article", document.getContentType());
        assertEquals(3, document.getTags().size());
        assertTrue(document.getTags().contains("Java"));
        assertEquals(85.5f, document.getHotScore());
    }

    @Test
    void testDocumentSerialization() throws Exception {
        // 创建测试文档
        ContentDocument document = ContentDocument.builder()
                .contentId(2L)
                .title("视频内容测试")
                .contentType("video")
                .tags(Arrays.asList("娱乐", "搞笑"))
                .publishTime(LocalDateTime.now())
                .hotScore(92.3f)
                .build();

        // 测试序列化
        String json = objectMapper.writeValueAsString(document);
        assertNotNull(json);
        assertTrue(json.contains("视频内容测试"));
        assertTrue(json.contains("video"));

        // 测试反序列化
        ContentDocument deserializedDocument = objectMapper.readValue(json, ContentDocument.class);
        assertEquals(document.getContentId(), deserializedDocument.getContentId());
        assertEquals(document.getTitle(), deserializedDocument.getTitle());
        assertEquals(document.getContentType(), deserializedDocument.getContentType());
    }

    @Test
    void testEmbeddingVector() {
        // 测试向量嵌入
        List<Float> embedding = Arrays.asList(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);
        
        ContentDocument document = ContentDocument.builder()
                .contentId(3L)
                .title("带向量的内容")
                .contentType("article")
                .embedding(embedding)
                .build();

        assertNotNull(document.getEmbedding());
        assertEquals(5, document.getEmbedding().size());
        assertEquals(0.3f, document.getEmbedding().get(2));
    }

    @Test
    void testProductContentDocument() {
        // 测试商品类型内容
        Map<String, Object> productData = new HashMap<>();
        productData.put("price", 299.99);
        productData.put("description", "高质量的商品描述");
        productData.put("images", Arrays.asList("image1.jpg", "image2.jpg"));

        ContentDocument document = ContentDocument.builder()
                .contentId(4L)
                .title("测试商品")
                .contentType("product")
                .tags(Arrays.asList("电子产品", "热销"))
                .extraData(productData)
                .hotScore(78.9f)
                .build();

        assertNotNull(document);
        assertEquals("product", document.getContentType());
        assertEquals(299.99, document.getExtraData().get("price"));
        assertTrue(document.getTags().contains("电子产品"));
    }
}