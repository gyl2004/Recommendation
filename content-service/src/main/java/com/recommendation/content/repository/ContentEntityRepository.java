package com.recommendation.content.repository;

import com.recommendation.content.entity.ContentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容Repository接口
 */
@Repository
public interface ContentEntityRepository extends JpaRepository<ContentEntity, Long> {

    /**
     * 根据内容类型和状态查找内容
     */
    Page<ContentEntity> findByContentTypeAndStatus(ContentEntity.ContentType contentType, 
                                                  ContentEntity.ContentStatus status, 
                                                  Pageable pageable);

    /**
     * 根据分类ID查找已发布的内容
     */
    Page<ContentEntity> findByCategoryIdAndStatus(Integer categoryId, 
                                                 ContentEntity.ContentStatus status, 
                                                 Pageable pageable);

    /**
     * 根据作者ID查找内容
     */
    Page<ContentEntity> findByAuthorIdAndStatus(Long authorId, 
                                               ContentEntity.ContentStatus status, 
                                               Pageable pageable);

    /**
     * 根据标题模糊查询已发布的内容
     */
    Page<ContentEntity> findByTitleContainingIgnoreCaseAndStatus(String title, 
                                                                ContentEntity.ContentStatus status, 
                                                                Pageable pageable);

    /**
     * 查找热门内容(按热度分数排序)
     */
    @Query("SELECT c FROM ContentEntity c WHERE c.status = 'PUBLISHED' ORDER BY c.hotScore DESC")
    Page<ContentEntity> findHotContents(Pageable pageable);

    /**
     * 查找最新发布的内容
     */
    @Query("SELECT c FROM ContentEntity c WHERE c.status = 'PUBLISHED' AND c.publishTime IS NOT NULL ORDER BY c.publishTime DESC")
    Page<ContentEntity> findLatestContents(Pageable pageable);

    /**
     * 查找指定时间范围内发布的内容
     */
    Page<ContentEntity> findByStatusAndPublishTimeBetween(ContentEntity.ContentStatus status, 
                                                         LocalDateTime startTime, 
                                                         LocalDateTime endTime, 
                                                         Pageable pageable);

    /**
     * 查找热度分数大于指定值的内容
     */
    List<ContentEntity> findByStatusAndHotScoreGreaterThanOrderByHotScoreDesc(ContentEntity.ContentStatus status, 
                                                                              BigDecimal minHotScore);

    /**
     * 查找浏览量最高的内容
     */
    @Query("SELECT c FROM ContentEntity c WHERE c.status = 'PUBLISHED' ORDER BY c.viewCount DESC")
    Page<ContentEntity> findMostViewedContents(Pageable pageable);

    /**
     * 统计各种状态的内容数量
     */
    long countByStatus(ContentEntity.ContentStatus status);

    /**
     * 统计指定作者的内容数量
     */
    long countByAuthorIdAndStatus(Long authorId, ContentEntity.ContentStatus status);

    /**
     * 统计指定分类的内容数量
     */
    long countByCategoryIdAndStatus(Integer categoryId, ContentEntity.ContentStatus status);

    /**
     * 统计指定内容类型的数量
     */
    long countByContentTypeAndStatus(ContentEntity.ContentType contentType, ContentEntity.ContentStatus status);

    /**
     * 增加内容浏览次数
     */
    @Modifying
    @Query("UPDATE ContentEntity c SET c.viewCount = c.viewCount + 1 WHERE c.id = :contentId")
    int incrementViewCount(@Param("contentId") Long contentId);

    /**
     * 增加内容点赞次数
     */
    @Modifying
    @Query("UPDATE ContentEntity c SET c.likeCount = c.likeCount + 1 WHERE c.id = :contentId")
    int incrementLikeCount(@Param("contentId") Long contentId);

    /**
     * 增加内容分享次数
     */
    @Modifying
    @Query("UPDATE ContentEntity c SET c.shareCount = c.shareCount + 1 WHERE c.id = :contentId")
    int incrementShareCount(@Param("contentId") Long contentId);

    /**
     * 增加内容评论次数
     */
    @Modifying
    @Query("UPDATE ContentEntity c SET c.commentCount = c.commentCount + 1 WHERE c.id = :contentId")
    int incrementCommentCount(@Param("contentId") Long contentId);

    /**
     * 批量更新内容状态
     */
    @Modifying
    @Query("UPDATE ContentEntity c SET c.status = :status WHERE c.id IN :contentIds")
    int updateStatusByIds(@Param("contentIds") List<Long> contentIds, @Param("status") ContentEntity.ContentStatus status);

    /**
     * 根据状态查找内容
     */
    Page<ContentEntity> findByStatus(ContentEntity.ContentStatus status, Pageable pageable);
}