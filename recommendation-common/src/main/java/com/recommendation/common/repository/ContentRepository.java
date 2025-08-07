package com.recommendation.common.repository;

import com.recommendation.common.entity.Content;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容Repository接口
 * 提供内容相关的数据访问操作
 */
@Repository
public interface ContentRepository extends BaseRepository<Content, Long> {

    /**
     * 根据内容类型和状态查找内容
     */
    Page<Content> findByContentTypeAndStatus(Content.ContentType contentType, 
                                           Content.ContentStatus status, 
                                           Pageable pageable);

    /**
     * 根据分类ID查找已发布的内容
     */
    Page<Content> findByCategoryIdAndStatus(Integer categoryId, 
                                          Content.ContentStatus status, 
                                          Pageable pageable);

    /**
     * 根据作者ID查找内容
     */
    Page<Content> findByAuthorIdAndStatus(Long authorId, 
                                        Content.ContentStatus status, 
                                        Pageable pageable);

    /**
     * 根据标题模糊查询已发布的内容
     */
    Page<Content> findByTitleContainingIgnoreCaseAndStatus(String title, 
                                                         Content.ContentStatus status, 
                                                         Pageable pageable);

    /**
     * 查找热门内容(按热度分数排序)
     */
    @Query("SELECT c FROM Content c WHERE c.status = 'PUBLISHED' ORDER BY c.hotScore DESC")
    Page<Content> findHotContents(Pageable pageable);

    /**
     * 查找最新发布的内容
     */
    @Query("SELECT c FROM Content c WHERE c.status = 'PUBLISHED' AND c.publishTime IS NOT NULL ORDER BY c.publishTime DESC")
    Page<Content> findLatestContents(Pageable pageable);

    /**
     * 查找指定时间范围内发布的内容
     */
    Page<Content> findByStatusAndPublishTimeBetween(Content.ContentStatus status, 
                                                  LocalDateTime startTime, 
                                                  LocalDateTime endTime, 
                                                  Pageable pageable);

    /**
     * 根据标签查找内容
     */
    @Query(value = "SELECT * FROM contents WHERE status = 'published' AND JSON_CONTAINS(tags, :tag)", 
           nativeQuery = true)
    Page<Content> findByTag(@Param("tag") String tag, Pageable pageable);

    /**
     * 查找包含任意指定标签的内容
     */
    @Query(value = "SELECT * FROM contents WHERE status = 'published' AND (" +
                   "JSON_OVERLAPS(tags, CAST(:tags AS JSON)))", 
           nativeQuery = true)
    Page<Content> findByAnyTags(@Param("tags") String tags, Pageable pageable);

    /**
     * 查找热度分数大于指定值的内容
     */
    List<Content> findByStatusAndHotScoreGreaterThanOrderByHotScoreDesc(Content.ContentStatus status, 
                                                                       BigDecimal minHotScore);

    /**
     * 查找浏览量最高的内容
     */
    @Query("SELECT c FROM Content c WHERE c.status = 'PUBLISHED' ORDER BY c.viewCount DESC")
    Page<Content> findMostViewedContents(Pageable pageable);

    /**
     * 统计各种状态的内容数量
     */
    long countByStatus(Content.ContentStatus status);

    /**
     * 统计指定作者的内容数量
     */
    long countByAuthorIdAndStatus(Long authorId, Content.ContentStatus status);

    /**
     * 统计指定分类的内容数量
     */
    long countByCategoryIdAndStatus(Integer categoryId, Content.ContentStatus status);

    /**
     * 统计指定内容类型的数量
     */
    long countByContentTypeAndStatus(Content.ContentType contentType, Content.ContentStatus status);

    /**
     * 增加内容浏览次数
     */
    @Modifying
    @Query("UPDATE Content c SET c.viewCount = c.viewCount + 1 WHERE c.id = :contentId")
    int incrementViewCount(@Param("contentId") Long contentId);

    /**
     * 增加内容点赞次数
     */
    @Modifying
    @Query("UPDATE Content c SET c.likeCount = c.likeCount + 1 WHERE c.id = :contentId")
    int incrementLikeCount(@Param("contentId") Long contentId);

    /**
     * 增加内容分享次数
     */
    @Modifying
    @Query("UPDATE Content c SET c.shareCount = c.shareCount + 1 WHERE c.id = :contentId")
    int incrementShareCount(@Param("contentId") Long contentId);

    /**
     * 增加内容评论次数
     */
    @Modifying
    @Query("UPDATE Content c SET c.commentCount = c.commentCount + 1 WHERE c.id = :contentId")
    int incrementCommentCount(@Param("contentId") Long contentId);

    /**
     * 批量更新内容状态
     */
    @Modifying
    @Query("UPDATE Content c SET c.status = :status WHERE c.id IN :contentIds")
    int updateStatusByIds(@Param("contentIds") List<Long> contentIds, @Param("status") Content.ContentStatus status);

    /**
     * 查找相似内容(基于分类和标签)
     */
    @Query(value = "SELECT c.* FROM contents c WHERE c.id != :contentId AND c.status = 'published' AND (" +
                   "c.category_id = :categoryId OR " +
                   "JSON_OVERLAPS(c.tags, :tags)" +
                   ") ORDER BY c.hot_score DESC LIMIT :limit", 
           nativeQuery = true)
    List<Content> findSimilarContents(@Param("contentId") Long contentId, 
                                    @Param("categoryId") Integer categoryId, 
                                    @Param("tags") String tags, 
                                    @Param("limit") Integer limit);

    /**
     * 查找用户可能感兴趣的内容(基于用户画像)
     */
    @Query(value = "SELECT c.* FROM contents c " +
                   "WHERE c.status = 'published' AND (" +
                   "c.category_id IN :categoryIds OR " +
                   "JSON_OVERLAPS(c.tags, CAST(:userInterests AS JSON))" +
                   ") ORDER BY c.hot_score DESC", 
           nativeQuery = true)
    Page<Content> findContentsByUserInterests(@Param("categoryIds") List<Integer> categoryIds, 
                                            @Param("userInterests") String userInterests, 
                                            Pageable pageable);

    /**
     * 查找需要更新热度分数的内容
     */
    @Query("SELECT c FROM Content c WHERE c.status = 'PUBLISHED' AND c.updatedAt < :threshold")
    List<Content> findContentsNeedingHotScoreUpdate(@Param("threshold") LocalDateTime threshold);

    /**
     * 根据状态查找内容
     */
    Page<Content> findByStatus(Content.ContentStatus status, Pageable pageable);
}