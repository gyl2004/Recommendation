package com.recommendation.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.content.dto.ContentFeatures;
import com.recommendation.content.entity.ContentEntity;
import com.recommendation.content.service.impl.ContentFeatureExtractionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 内容特征提取服务测试
 * 测试需求6.1, 6.2, 6.3: 特征工程处理
 */
@ExtendWith(MockitoExtension.class)
class ContentFeatureExtractionServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ContentFeatureExtractionServiceImpl featureExtractionService;

    private ContentEntity articleContent;
    private ContentEntity videoContent;
    private ContentEntity productContent;

    @BeforeEach
    void setUp() {
        // 创建文章内容测试数据
        articleContent = new ContentEntity();
        articleContent.setId(1L);
        articleContent.setTitle("人工智能技术发展趋势");
        articleContent.setContentType(ContentEntity.ContentType.ARTICLE);
        articleContent.setContentData("{\"content\":\"人工智能是当今科技发展的重要方向，深度学习技术不断突破。\",\"summary\":\"AI技术发展概述\"}");
        articleContent.setTags("[\"AI\",\"技术\",\"深度学习\"]");
        articleContent.setCategoryId(1);
        articleContent.setAuthorId(100L);
        articleContent.setViewCount(1000L);
        articleContent.setLikeCount(50L);
        articleContent.setShareCount(10L);
        articleContent.setCommentCount(20L);
        articleContent.setHotScore(BigDecimal.valueOf(85.5));
        articleContent.setPublishTime(LocalDateTime.now().minusHours(2));

        // 创建视频内容测试数据
        videoContent = new ContentEntity();
        videoContent.setId(2L);
        videoContent.setTitle("机器学习入门教程");
        videoContent.setContentType(ContentEntity.ContentType.VIDEO);
        videoContent.setContentData("{\"videoUrl\":\"http://example.com/video.mp4\",\"duration\":1800,\"width\":1920,\"height\":1080,\"fileSize\":104857600,\"format\":\"mp4\",\"frameRate\":30.0,\"bitRate\":2000,\"hasAudio\":true}");
        videoContent.setTags("[\"教程\",\"机器学习\"]");
        videoContent.setCategoryId(2);
        videoContent.setAuthorId(101L);
        videoContent.setViewCount(5000L);
        videoContent.setLikeCount(200L);
        videoContent.setShareCount(50L);
        videoContent.setCommentCount(100L);
        videoContent.setHotScore(BigDecimal.valueOf(92.3));
        videoContent.setPublishTime(LocalDateTime.now().minusHours(1));

        // 创建商品内容测试数据
        productContent = new ContentEntity();
        productContent.setId(3L);
        productContent.setTitle("智能手机 Pro Max");
        productContent.setContentType(ContentEntity.ContentType.PRODUCT);
        productContent.setContentData("{\"price\":5999.0,\"brand\":\"TechBrand\",\"description\":\"高性能智能手机，配备先进的AI芯片和优秀的摄像系统。\",\"images\":[\"img1.jpg\",\"img2.jpg\",\"img3.jpg\"],\"rating\":4.5,\"salesCount\":1500,\"stockStatus\":\"in_stock\"}");
        productContent.setTags("[\"手机\",\"智能\",\"高端\"]");
        productContent.setCategoryId(3);
        productContent.setAuthorId(102L);
        productContent.setViewCount(8000L);
        productContent.setLikeCount(300L);
        productContent.setShareCount(80L);
        productContent.setCommentCount(150L);
        productContent.setHotScore(BigDecimal.valueOf(88.7));
        productContent.setPublishTime(LocalDateTime.now().minusHours(3));
    }

    @Test
    void testExtractArticleFeatures() {
        // 测试文章特征提取
        ContentFeatures features = featureExtractionService.extractFeatures(articleContent);

        assertNotNull(features);
        assertEquals(1L, features.getContentId());
        assertEquals("article", features.getContentType());

        // 验证文本特征
        ContentFeatures.TextFeatures textFeatures = features.getTextFeatures();
        assertNotNull(textFeatures);
        assertNotNull(textFeatures.getTfidfVector());
        assertNotNull(textFeatures.getWordEmbedding());
        assertEquals(128, textFeatures.getWordEmbedding().size());
        assertNotNull(textFeatures.getKeywords());
        assertTrue(textFeatures.getTextLength() > 0);
        assertTrue(textFeatures.getSentenceCount() > 0);
        assertTrue(textFeatures.getParagraphCount() > 0);
        assertNotNull(textFeatures.getSentimentScore());

        // 验证通用特征
        ContentFeatures.CommonFeatures commonFeatures = features.getCommonFeatures();
        assertNotNull(commonFeatures);
        assertEquals(1, commonFeatures.getCategoryId());
        assertEquals(100L, commonFeatures.getAuthorId());
        assertEquals(1000L, commonFeatures.getViewCount());
        assertEquals(50L, commonFeatures.getLikeCount());
        assertTrue(commonFeatures.getEngagementRate() > 0);
    }

    @Test
    void testExtractVideoFeatures() {
        // 测试视频特征提取
        ContentFeatures features = featureExtractionService.extractFeatures(videoContent);

        assertNotNull(features);
        assertEquals(2L, features.getContentId());
        assertEquals("video", features.getContentType());

        // 验证视频特征
        ContentFeatures.VideoFeatures videoFeatures = features.getVideoFeatures();
        assertNotNull(videoFeatures);
        assertEquals(1800, videoFeatures.getDuration());
        assertEquals(1920, videoFeatures.getWidth());
        assertEquals(1080, videoFeatures.getHeight());
        assertEquals(104857600L, videoFeatures.getFileSize());
        assertEquals("mp4", videoFeatures.getFormat());
        assertEquals(30.0, videoFeatures.getFrameRate());
        assertEquals(2000, videoFeatures.getBitRate());
        assertTrue(videoFeatures.getHasAudio());
        assertNotNull(videoFeatures.getQualityScore());
        assertTrue(videoFeatures.getQualityScore() > 0 && videoFeatures.getQualityScore() <= 1);
        assertEquals("long", videoFeatures.getDurationCategory());

        // 验证通用特征
        ContentFeatures.CommonFeatures commonFeatures = features.getCommonFeatures();
        assertNotNull(commonFeatures);
        assertEquals(2, commonFeatures.getCategoryId());
        assertEquals(101L, commonFeatures.getAuthorId());
    }

    @Test
    void testExtractProductFeatures() {
        // 测试商品特征提取
        ContentFeatures features = featureExtractionService.extractFeatures(productContent);

        assertNotNull(features);
        assertEquals(3L, features.getContentId());
        assertEquals("product", features.getContentType());

        // 验证商品特征
        ContentFeatures.ProductFeatures productFeatures = features.getProductFeatures();
        assertNotNull(productFeatures);
        assertEquals(5999.0, productFeatures.getPrice());
        assertEquals("high", productFeatures.getPriceRange());
        assertEquals("TechBrand", productFeatures.getBrand());
        assertNotNull(productFeatures.getAttributeVector());
        assertEquals(3, productFeatures.getImageCount());
        assertTrue(productFeatures.getDescriptionLength() > 0);
        assertEquals(4.5, productFeatures.getRating());
        assertEquals(1500, productFeatures.getSalesCount());
        assertEquals("in_stock", productFeatures.getStockStatus());
        assertEquals("hot", productFeatures.getProductStatus());

        // 验证文本特征（商品描述）
        ContentFeatures.TextFeatures textFeatures = features.getTextFeatures();
        assertNotNull(textFeatures);
        assertNotNull(textFeatures.getTfidfVector());
        assertTrue(textFeatures.getTextLength() > 0);

        // 验证通用特征
        ContentFeatures.CommonFeatures commonFeatures = features.getCommonFeatures();
        assertNotNull(commonFeatures);
        assertEquals(3, commonFeatures.getCategoryId());
        assertEquals(102L, commonFeatures.getAuthorId());
    }

    @Test
    void testExtractTextFeatures() {
        String testText = "人工智能技术发展迅速，深度学习算法不断优化，为各行各业带来了革命性的变化。";
        
        ContentFeatures.TextFeatures textFeatures = featureExtractionService.extractTextFeatures(articleContent, testText);

        assertNotNull(textFeatures);
        assertNotNull(textFeatures.getTfidfVector());
        assertFalse(textFeatures.getTfidfVector().isEmpty());
        assertNotNull(textFeatures.getWordEmbedding());
        assertEquals(128, textFeatures.getWordEmbedding().size());
        assertNotNull(textFeatures.getKeywords());
        assertFalse(textFeatures.getKeywords().isEmpty());
        assertEquals(testText.length(), textFeatures.getTextLength());
        assertTrue(textFeatures.getSentenceCount() > 0);
        assertTrue(textFeatures.getParagraphCount() > 0);
        assertNotNull(textFeatures.getSentimentScore());
        assertTrue(textFeatures.getSentimentScore() >= -1.0 && textFeatures.getSentimentScore() <= 1.0);
    }

    @Test
    void testExtractVideoFeaturesOnly() {
        ContentFeatures.VideoFeatures videoFeatures = featureExtractionService.extractVideoFeatures(videoContent);

        assertNotNull(videoFeatures);
        assertEquals(1800, videoFeatures.getDuration());
        assertEquals("long", videoFeatures.getDurationCategory());
        assertTrue(videoFeatures.getQualityScore() > 0);
    }

    @Test
    void testExtractProductFeaturesOnly() {
        ContentFeatures.ProductFeatures productFeatures = featureExtractionService.extractProductFeatures(productContent);

        assertNotNull(productFeatures);
        assertEquals(5999.0, productFeatures.getPrice());
        assertEquals("high", productFeatures.getPriceRange());
        assertEquals("hot", productFeatures.getProductStatus());
    }

    @Test
    void testExtractCommonFeatures() {
        ContentFeatures.CommonFeatures commonFeatures = featureExtractionService.extractCommonFeatures(articleContent);

        assertNotNull(commonFeatures);
        assertNotNull(commonFeatures.getTags());
        assertEquals(1, commonFeatures.getCategoryId());
        assertEquals(100L, commonFeatures.getAuthorId());
        assertEquals(1000L, commonFeatures.getViewCount());
        assertEquals(50L, commonFeatures.getLikeCount());
        assertEquals(10L, commonFeatures.getShareCount());
        assertEquals(20L, commonFeatures.getCommentCount());
        assertTrue(commonFeatures.getEngagementRate() > 0);
        assertTrue(commonFeatures.getFreshnessHours() >= 0);
        assertNotNull(commonFeatures.getCategoryPath());
    }

    @Test
    void testNormalizeFeatures() {
        ContentFeatures features = featureExtractionService.extractFeatures(productContent);
        ContentFeatures normalizedFeatures = featureExtractionService.normalizeFeatures(features);

        assertNotNull(normalizedFeatures);
        
        // 验证商品特征标准化
        if (normalizedFeatures.getProductFeatures() != null) {
            ContentFeatures.ProductFeatures productFeatures = normalizedFeatures.getProductFeatures();
            assertTrue(productFeatures.getRating() >= 0.0 && productFeatures.getRating() <= 1.0);
        }

        // 验证通用特征标准化
        if (normalizedFeatures.getCommonFeatures() != null) {
            ContentFeatures.CommonFeatures commonFeatures = normalizedFeatures.getCommonFeatures();
            assertTrue(commonFeatures.getEngagementRate() >= 0.0 && commonFeatures.getEngagementRate() <= 1.0);
        }
    }

    @Test
    void testEmptyTextFeatures() {
        ContentFeatures.TextFeatures textFeatures = featureExtractionService.extractTextFeatures(articleContent, "");

        assertNotNull(textFeatures);
        assertTrue(textFeatures.getTfidfVector().isEmpty());
        assertEquals(0, textFeatures.getTextLength());
        assertEquals(0, textFeatures.getSentenceCount());
        assertEquals(0, textFeatures.getParagraphCount());
        assertEquals(0.0, textFeatures.getSentimentScore());
    }

    @Test
    void testNullTextFeatures() {
        ContentFeatures.TextFeatures textFeatures = featureExtractionService.extractTextFeatures(articleContent, null);

        assertNotNull(textFeatures);
        assertTrue(textFeatures.getTfidfVector().isEmpty());
        assertEquals(0, textFeatures.getTextLength());
    }

    @Test
    void testVideoDurationCategories() {
        // 测试短视频
        ContentEntity shortVideo = new ContentEntity();
        shortVideo.setContentType(ContentEntity.ContentType.VIDEO);
        shortVideo.setContentData("{\"duration\":30}");
        
        ContentFeatures.VideoFeatures shortVideoFeatures = featureExtractionService.extractVideoFeatures(shortVideo);
        assertEquals("short", shortVideoFeatures.getDurationCategory());

        // 测试中视频
        ContentEntity mediumVideo = new ContentEntity();
        mediumVideo.setContentType(ContentEntity.ContentType.VIDEO);
        mediumVideo.setContentData("{\"duration\":300}");
        
        ContentFeatures.VideoFeatures mediumVideoFeatures = featureExtractionService.extractVideoFeatures(mediumVideo);
        assertEquals("medium", mediumVideoFeatures.getDurationCategory());

        // 测试长视频
        ContentEntity longVideo = new ContentEntity();
        longVideo.setContentType(ContentEntity.ContentType.VIDEO);
        longVideo.setContentData("{\"duration\":3600}");
        
        ContentFeatures.VideoFeatures longVideoFeatures = featureExtractionService.extractVideoFeatures(longVideo);
        assertEquals("long", longVideoFeatures.getDurationCategory());
    }

    @Test
    void testProductPriceRanges() {
        // 测试低价商品
        ContentEntity lowPriceProduct = new ContentEntity();
        lowPriceProduct.setContentType(ContentEntity.ContentType.PRODUCT);
        lowPriceProduct.setContentData("{\"price\":50.0}");
        
        ContentFeatures.ProductFeatures lowPriceFeatures = featureExtractionService.extractProductFeatures(lowPriceProduct);
        assertEquals("low", lowPriceFeatures.getPriceRange());

        // 测试中价商品
        ContentEntity mediumPriceProduct = new ContentEntity();
        mediumPriceProduct.setContentType(ContentEntity.ContentType.PRODUCT);
        mediumPriceProduct.setContentData("{\"price\":500.0}");
        
        ContentFeatures.ProductFeatures mediumPriceFeatures = featureExtractionService.extractProductFeatures(mediumPriceProduct);
        assertEquals("medium", mediumPriceFeatures.getPriceRange());

        // 测试高价商品
        ContentEntity highPriceProduct = new ContentEntity();
        highPriceProduct.setContentType(ContentEntity.ContentType.PRODUCT);
        highPriceProduct.setContentData("{\"price\":5000.0}");
        
        ContentFeatures.ProductFeatures highPriceFeatures = featureExtractionService.extractProductFeatures(highPriceProduct);
        assertEquals("high", highPriceFeatures.getPriceRange());
    }

    @Test
    void testSentimentAnalysis() {
        // 测试正面情感
        String positiveText = "这个产品很好，我很喜欢，强烈推荐大家购买。";
        ContentFeatures.TextFeatures positiveFeatures = featureExtractionService.extractTextFeatures(articleContent, positiveText);
        assertTrue(positiveFeatures.getSentimentScore() > 0);

        // 测试负面情感
        String negativeText = "这个产品很差，质量糟糕，非常失望。";
        ContentFeatures.TextFeatures negativeFeatures = featureExtractionService.extractTextFeatures(articleContent, negativeText);
        assertTrue(negativeFeatures.getSentimentScore() < 0);

        // 测试中性情感
        String neutralText = "这是一个普通的产品描述。";
        ContentFeatures.TextFeatures neutralFeatures = featureExtractionService.extractTextFeatures(articleContent, neutralText);
        assertEquals(0.0, neutralFeatures.getSentimentScore());
    }

    @Test
    void testEngagementRateCalculation() {
        ContentFeatures.CommonFeatures commonFeatures = featureExtractionService.extractCommonFeatures(articleContent);
        
        // 验证互动率计算：(点赞 + 分享 + 评论) / 浏览量
        double expectedRate = (50.0 + 10.0 + 20.0) / 1000.0;
        assertEquals(expectedRate, commonFeatures.getEngagementRate(), 0.001);
    }

    @Test
    void testFreshnessCalculation() {
        ContentFeatures.CommonFeatures commonFeatures = featureExtractionService.extractCommonFeatures(articleContent);
        
        // 验证新鲜度计算（应该是2小时左右）
        assertTrue(commonFeatures.getFreshnessHours() >= 1 && commonFeatures.getFreshnessHours() <= 3);
    }
}