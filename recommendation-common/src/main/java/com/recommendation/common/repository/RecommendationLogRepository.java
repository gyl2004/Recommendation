package com.recommendation.common.repository;

import com.recommendation.common.entity.RecommendationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 推荐记录Repository接口
 * 提供推荐记录相关的数据访问操作
 */
@Repository
public interface RecommendationLogRepository extends BaseRepository<RecommendationLog, Long> {

    /**
     * 根据用户ID查找推荐记录
     */
    Page<RecommendationLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 根据算法类型查找推荐记录
     */
    Page<RecommendationLog> findByAlgorithmTypeOrderByCreatedAtDesc(String algorithmType, Pageable pageable);

    /**
     * 根据用户ID和算法类型查找推荐记录
     */
    List<RecommendationLog> findByUserIdAndAlgorithmTypeOrderByCreatedAtDesc(Long userId, String algorithmType);

    /**
     * 查找指定时间范围内的推荐记录
     */
    Page<RecommendationLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startTime, 
                                                                      LocalDateTime endTime, 
                                                                      Pageable pageable);

    /**
     * 查找用户最近的推荐记录
     */
    @Query("SELECT rl FROM RecommendationLog rl WHERE rl.userId = :userId AND rl.createdAt >= :since ORDER BY rl.createdAt DESC")
    List<RecommendationLog> findRecentRecommendations(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * 统计用户的推荐次数
     */
    long countByUserId(Long userId);

    /**
     * 统计算法类型的使用次数
     */
    long countByAlgorithmType(String algorithmType);

    /**
     * 统计指定时间范围内的推荐次数
     */
    long countByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查找推荐响应时间统计
     */
    @Query("SELECT AVG(rl.responseTime), MIN(rl.responseTime), MAX(rl.responseTime) " +
           "FROM RecommendationLog rl " +
           "WHERE rl.createdAt >= :since AND rl.responseTime IS NOT NULL")
    Object[] findResponseTimeStatistics(@Param("since") LocalDateTime since);

    /**
     * 查找各算法的平均响应时间
     */
    @Query("SELECT rl.algorithmType, AVG(rl.responseTime) as avgResponseTime " +
           "FROM RecommendationLog rl " +
           "WHERE rl.createdAt >= :since AND rl.responseTime IS NOT NULL " +
           "GROUP BY rl.algorithmType " +
           "ORDER BY avgResponseTime ASC")
    List<Object[]> findAlgorithmResponseTimeStatistics(@Param("since") LocalDateTime since);

    /**
     * 查找推荐内容的曝光统计
     */
    @Query(value = "SELECT content_id, COUNT(*) as exposure_count " +
                   "FROM recommendation_logs rl, " +
                   "JSON_TABLE(rl.content_ids, '$[*]' COLUMNS (content_id BIGINT PATH '$')) jt " +
                   "WHERE rl.created_at >= :since " +
                   "GROUP BY content_id " +
                   "ORDER BY exposure_count DESC", 
           nativeQuery = true)
    List<Object[]> findContentExposureStatistics(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * 查找用户推荐的内容ID列表(去重)
     */
    @Query(value = "SELECT DISTINCT content_id " +
                   "FROM recommendation_logs rl, " +
                   "JSON_TABLE(rl.content_ids, '$[*]' COLUMNS (content_id BIGINT PATH '$')) jt " +
                   "WHERE rl.user_id = :userId AND rl.created_at >= :since", 
           nativeQuery = true)
    List<Long> findRecommendedContentIds(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * 查找算法使用频率统计
     */
    @Query("SELECT rl.algorithmType, COUNT(rl) as usageCount " +
           "FROM RecommendationLog rl " +
           "WHERE rl.createdAt >= :since " +
           "GROUP BY rl.algorithmType " +
           "ORDER BY usageCount DESC")
    List<Object[]> findAlgorithmUsageStatistics(@Param("since") LocalDateTime since);

    /**
     * 查找推荐效果统计(需要结合用户行为数据)
     */
    @Query("SELECT rl.algorithmType, " +
           "COUNT(rl) as totalRecommendations, " +
           "COUNT(DISTINCT ub.userId) as activeUsers " +
           "FROM RecommendationLog rl " +
           "LEFT JOIN UserBehavior ub ON rl.userId = ub.userId " +
           "AND ub.createdAt BETWEEN rl.createdAt AND DATE_ADD(rl.createdAt, INTERVAL 1 HOUR) " +
           "WHERE rl.createdAt >= :since " +
           "GROUP BY rl.algorithmType")
    List<Object[]> findRecommendationEffectivenessStatistics(@Param("since") LocalDateTime since);

    /**
     * 查找用户推荐历史(包含内容信息)
     */
    @Query("SELECT rl, c FROM RecommendationLog rl " +
           "LEFT JOIN Content c ON c.id IN (SELECT content_id FROM JSON_TABLE(rl.contentIds, '$[*]' COLUMNS (content_id BIGINT PATH '$')) jt) " +
           "WHERE rl.userId = :userId " +
           "ORDER BY rl.createdAt DESC")
    List<Object[]> findUserRecommendationHistory(@Param("userId") Long userId, Pageable pageable);

    /**
     * 删除过期的推荐记录
     */
    @Query("DELETE FROM RecommendationLog rl WHERE rl.createdAt < :expireTime")
    int deleteExpiredRecommendations(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 查找推荐数量分布统计
     */
    @Query(value = "SELECT JSON_LENGTH(content_ids) as recommendation_count, COUNT(*) as frequency " +
                   "FROM recommendation_logs " +
                   "WHERE created_at >= :since " +
                   "GROUP BY JSON_LENGTH(content_ids) " +
                   "ORDER BY recommendation_count", 
           nativeQuery = true)
    List<Object[]> findRecommendationCountDistribution(@Param("since") LocalDateTime since);

    /**
     * 查找用户推荐偏好(基于请求参数)
     */
    @Query(value = "SELECT JSON_EXTRACT(request_params, '$.contentType') as content_type, COUNT(*) as count " +
                   "FROM recommendation_logs " +
                   "WHERE user_id = :userId AND created_at >= :since " +
                   "AND JSON_EXTRACT(request_params, '$.contentType') IS NOT NULL " +
                   "GROUP BY JSON_EXTRACT(request_params, '$.contentType') " +
                   "ORDER BY count DESC", 
           nativeQuery = true)
    List<Object[]> findUserRecommendationPreferences(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}