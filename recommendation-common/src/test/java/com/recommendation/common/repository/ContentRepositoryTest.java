package com.recommendation.common.repository;

import com.recommendation.common.config.TestConfig;
import com.recommendation.common.entity.Category;
import com.recommendation.common.entity.Content;
import com.recommendation.common.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContentRepository测试类
 */
@DataJpaTest
@Import(TestConfig.class)
@ActiveProfiles("test")
class ContentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ContentRepository contentRepository;

    private User testUser;
    private Category testCategory;
    private Content testArticle;
    private Content testVideo;

    @BeforeEach
    void setUp() {
        // 创建测试用户
        testUser = new User();
        testUser.setUsername("testauthor");
        testUser.setEmail("author@example.com");
        testUser.setStatus(1);
        entityManager.persistAndFlush(testUser);

        // 创建测试分类
        testCategory = new Category();
        testCategory.setName("科技");
        testCategory.setStatus(1);
        entityManager.persistAndFlush(testCategory);

        // 创建测试文章
        testArticle = new Content();
        testArticle.setTitle("人工智能的未来");
        testArticle.setContentType(Content.ContentType.ARTICLE);
        
        Map<String, Object> articleData = new HashMap<>();
        articleData.put("content", "人工智能技术正在快速发展...");
        articleData.put("wordCount", 1500);
        articleData.put("readingTime", 5);
        testArticle.setContentData(articleData);
        
        testArticle.setTags(List.of("AI", "技术", "未来"));
        testArticle.setCategoryId(testCategory.getId());
        testArticle.setAuthorId(testUser.getId());
        testArticle.setStatus(Content.ContentStatus.PUBLISHED);
        testArticle.setViewCount(1000L);
        testArticle.setLikeCount(50L);
        testArticle.setHotScore(BigDecimal.valueOf(85.5));
        testArticle.setPublishTime(LocalDateTime.now().minusDays(1));
        entityManager.persistAndFlush(testArticle);

        // 创建测试视频
        testVideo = new Content();
        testVideo.setTitle("最新手机评测");
        testVideo.setContentType(Content.ContentType.VIDEO);
        
        Map<String, Object> videoData = new HashMap<>();
        videoData.put("videoUrl", "https://example.com/video1.mp4");
        videoData.put("duration", 600);
        videoData.put("resolution", "1080p");
        testVideo.setContentData(videoData);
        
        testVideo.setTags(List.of("手机", "评测", "科技"));
        testVideo.setCategoryId(testCategory.getId());
        testVideo.setAuthorId(testUser.getId());
        testVideo.setStatus(Content.ContentStatus.DRAFT);
        testVideo.setViewCount(500L);
        testVideo.setLikeCount(25L);
        testVideo.setHotScore(BigDecimal.valueOf(65.2));
        entityManager.persistAndFlush(testVideo);
    }

    @Test
    void testFindByContentTypeAndStatus() {
        // 测试根据内容类型和状态查找内容
        Pageable pageable = PageRequest.of(0, 10);
        Page<Content> articles = contentRepository.findByContentTypeAndStatus(
            Content.ContentType.ARTICLE, Content.ContentStatus.PUBLISHED, pageable);
        
        assertThat(articles.getContent()).hasSize(1);
        assertThat(articles.getContent().get(0).getTitle()).isEqualTo("人工智能的未来");
    }

    @Test
    void testFindByCategoryIdAndStatus() {
        // 测试根据分类ID查找已发布的内容
        Pageable pageable = PageRequest.of(0, 10);
        Page<Content> contents = contentRepository.findByCategoryIdAndStatus(
            testCategory.getId(), Content.ContentStatus.PUBLISHED, pageable);
        
        assertThat(contents.getContent()).hasSize(1);
        assertThat(contents.getContent().get(0).getTitle()).isEqualTo("人工智能的未来");
    }

    @Test
    void testFindByAuthorIdAndStatus() {
        // 测试根据作者ID查找内容
        Pageable pageable = PageRequest.of(0, 10);
        Page<Content> publishedContents = contentRepository.findByAuthorIdAndStatus(
            testUser.getId(), Content.ContentStatus.PUBLISHED, pageable);
        Page<Content> draftContents = contentRepository.findByAuthorIdAndStatus(
            testUser.getId(), Content.ContentStatus.DRAFT, pageable);
        
        assertThat(publishedContents.getContent()).hasSize(1);
        assertThat(draftContents.getContent()).hasSize(1);
    }

    @Test
    void testFindByTitleContainingIgnoreCaseAndStatus() {
        // 测试根据标题模糊查询已发布的内容
        Pageable pageable = PageRequest.of(0, 10);
        Page<Content> contents = contentRepository.findByTitleContainingIgnoreCaseAndStatus(
            "人工智能", Content.ContentStatus.PUBLISHED, pageable);
        
        assertThat(contents.getContent()).hasSize(1);
        assertThat(contents.getContent().get(0).getTitle()).contains("人工智能");
    }

    @Test
    void testFindHotContents() {
        // 测试查找热门内容
        Pageable pageable = PageRequest.of(0, 10);
        Page<Content> hotContents = contentRepository.findHotContents(pageable);
        
        assertThat(hotContents.getContent()).hasSize(1); // 只有已发布的内容
        assertThat(hotContents.getContent().get(0).getHotScore()).isEqualTo(BigDecimal.valueOf(85.5));
    }

    @Test
    void testFindLatestContents() {
        // 测试查找最新发布的内容
        Pageable pageable = PageRequest.of(0, 10);
        Page<Content> latestContents = contentRepository.findLatestContents(pageable);
        
        assertThat(latestContents.getContent()).hasSize(1);
        assertThat(latestContents.getContent().get(0).getPublishTime()).isNotNull();
    }

    @Test
    void testFindStatusAndPublishTimeBetween() {
        // 测试查找指定时间范围内发布的内容
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        LocalDateTime end = LocalDateTime.now();
        Pageable pageable = PageRequest.of(0, 10);
        
        Page<Content> contents = contentRepository.findByStatusAndPublishTimeBetween(
            Content.ContentStatus.PUBLISHED, start, end, pageable);
        
        assertThat(contents.getContent()).hasSize(1);
    }

    @Test
    void testFindMostViewedContents() {
        // 测试查找浏览量最高的内容
        Pageable pageable = PageRequest.of(0, 10);
        Page<Content> mostViewed = contentRepository.findMostViewedContents(pageable);
        
        assertThat(mostViewed.getContent()).hasSize(1);
        assertThat(mostViewed.getContent().get(0).getViewCount()).isEqualTo(1000L);
    }

    @Test
    void testCountMethods() {
        // 测试各种统计方法
        long publishedCount = contentRepository.countByStatus(Content.ContentStatus.PUBLISHED);
        long draftCount = contentRepository.countByStatus(Content.ContentStatus.DRAFT);
        long authorContentCount = contentRepository.countByAuthorIdAndStatus(testUser.getId(), Content.ContentStatus.PUBLISHED);
        long categoryContentCount = contentRepository.countByCategoryIdAndStatus(testCategory.getId(), Content.ContentStatus.PUBLISHED);
        long articleCount = contentRepository.countByContentTypeAndStatus(Content.ContentType.ARTICLE, Content.ContentStatus.PUBLISHED);
        
        assertThat(publishedCount).isEqualTo(1);
        assertThat(draftCount).isEqualTo(1);
        assertThat(authorContentCount).isEqualTo(1);
        assertThat(categoryContentCount).isEqualTo(1);
        assertThat(articleCount).isEqualTo(1);
    }

    @Test
    void testIncrementMethods() {
        // 测试增加计数的方法
        Long contentId = testArticle.getId();
        
        int viewResult = contentRepository.incrementViewCount(contentId);
        int likeResult = contentRepository.incrementLikeCount(contentId);
        int shareResult = contentRepository.incrementShareCount(contentId);
        int commentResult = contentRepository.incrementCommentCount(contentId);
        
        assertThat(viewResult).isEqualTo(1);
        assertThat(likeResult).isEqualTo(1);
        assertThat(shareResult).isEqualTo(1);
        assertThat(commentResult).isEqualTo(1);
        
        // 刷新实体以获取更新后的值
        entityManager.refresh(testArticle);
        assertThat(testArticle.getViewCount()).isEqualTo(1001L);
        assertThat(testArticle.getLikeCount()).isEqualTo(51L);
        assertThat(testArticle.getShareCount()).isEqualTo(1L);
        assertThat(testArticle.getCommentCount()).isEqualTo(1L);
    }

    @Test
    void testContentEntityMethods() {
        // 测试Content实体类的方法
        assertThat(testArticle.isPublished()).isTrue();
        assertThat(testVideo.isPublished()).isFalse();
        
        // 测试内容属性操作
        testArticle.setContentAttribute("newKey", "newValue");
        assertThat(testArticle.getContentAttribute("newKey")).isEqualTo("newValue");
        assertThat(testArticle.getContentAttribute("wordCount")).isEqualTo(1500);
        
        // 测试计数增加方法
        Long originalViewCount = testArticle.getViewCount();
        testArticle.incrementViewCount();
        assertThat(testArticle.getViewCount()).isEqualTo(originalViewCount + 1);
        
        // 测试热度分数更新
        testArticle.updateHotScore();
        assertThat(testArticle.getHotScore()).isNotNull();
    }

    @Test
    void testFindByStatusAndHotScoreGreaterThan() {
        // 测试查找热度分数大于指定值的内容
        List<Content> hotContents = contentRepository.findByStatusAndHotScoreGreaterThanOrderByHotScoreDesc(
            Content.ContentStatus.PUBLISHED, BigDecimal.valueOf(80.0));
        
        assertThat(hotContents).hasSize(1);
        assertThat(hotContents.get(0).getHotScore()).isGreaterThan(BigDecimal.valueOf(80.0));
    }
}