package com.recommendation.common.repository;

import com.recommendation.common.entity.UserBehavior;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户行为Repository接口
 * 提供用户行为相关的数据访问操作
 */
@Repository
public interface UserBehaviorRepository extends BaseRepository<UserBehavior, Long> {

    /**
     * 根据用户ID查找行为记录
     */
    Page<UserBehavior> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 根据内容ID查找行为记录
     */
    Page<UserBehavior> findByContentIdOrderByCreatedAtDesc(Long contentId, Pageable pageable);

    /**
     * 根据用户ID和行为类型查找记录
     */
    List<UserBehavior> findByUserIdAndActionTypeOrderByCreatedAtDesc(Long userId, UserBehavior.ActionType actionType);

    /**
     * 根据用户ID和内容ID查找行为记录
     */
    List<UserBehavior> findByUserIdAndContentIdOrderByCreatedAtDesc(Long userId, Long contentId);

    /**
     * 查找指定时间范围内的用户行为
     */
    Page<UserBehavior> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, 
                                                                          LocalDateTime startTime, 
                                                                          LocalDateTime endTime, 
                                                                          Pageable pageable);

    /**
     * 查找用户最近的行为记录
     */
    @Query("SELECT ub FROM UserBehavior ub WHERE ub.userId = :userId AND ub.createdAt >= :since ORDER BY ub.createdAt DESC")
    List<UserBehavior> findRecentBehaviors(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * 统计用户的行为次数
     */
    long countByUserIdAndActionType(Long userId, UserBehavior.ActionType actionType);

    /**
     * 统计内容的行为次数
     */
    long countByContentIdAndActionType(Long contentId, UserBehavior.ActionType actionType);

    /**
     * 统计用户在指定时间范围内的行为次数
     */
    long countByUserIdAndCreatedAtBetween(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查找用户喜欢的内容(点赞、收藏等正向行为)
     */
    @Query("SELECT ub FROM UserBehavior ub WHERE ub.userId = :userId AND ub.actionType IN ('LIKE', 'COLLECT', 'SHARE') ORDER BY ub.createdAt DESC")
    List<UserBehavior> findUserPreferredContents(@Param("userId") Long userId);

    /**
     * 查找用户浏览过的内容ID列表
     */
    @Query("SELECT DISTINCT ub.contentId FROM UserBehavior ub WHERE ub.userId = :userId AND ub.actionType = 'VIEW' ORDER BY ub.createdAt DESC")
    List<Long> findViewedContentIds(@Param("userId") Long userId);

    /**
     * 查找用户最近浏览的内容ID列表
     */
    @Query("SELECT DISTINCT ub.contentId FROM UserBehavior ub WHERE ub.userId = :userId AND ub.actionType = 'VIEW' AND ub.createdAt >= :since ORDER BY ub.createdAt DESC")
    List<Long> findRecentViewedContentIds(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * 查找热门内容(基于用户行为统计)
     */
    @Query("SELECT ub.contentId, COUNT(ub) as behaviorCount FROM UserBehavior ub " +
           "WHERE ub.createdAt >= :since " +
           "GROUP BY ub.contentId " +
           "ORDER BY behaviorCount DESC")
    List<Object[]> findHotContentsByBehavior(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * 查找用户行为模式(按小时统计)
     */
    @Query(value = "SELECT HOUR(created_at) as hour, COUNT(*) as count " +
                   "FROM user_behaviors " +
                   "WHERE user_id = :userId AND created_at >= :since " +
                   "GROUP BY HOUR(created_at) " +
                   "ORDER BY hour", 
           nativeQuery = true)
    List<Object[]> findUserBehaviorPatternByHour(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * 查找相似用户(基于共同行为的内容)
     */
    @Query("SELECT ub2.userId, COUNT(DISTINCT ub2.contentId) as commonContents " +
           "FROM UserBehavior ub1, UserBehavior ub2 " +
           "WHERE ub1.userId = :userId AND ub1.userId != ub2.userId " +
           "AND ub1.contentId = ub2.contentId " +
           "AND ub1.actionType IN ('LIKE', 'COLLECT', 'SHARE') " +
           "AND ub2.actionType IN ('LIKE', 'COLLECT', 'SHARE') " +
           "GROUP BY ub2.userId " +
           "HAVING commonContents >= :minCommonContents " +
           "ORDER BY commonContents DESC")
    List<Object[]> findSimilarUsers(@Param("userId") Long userId, @Param("minCommonContents") Long minCommonContents);

    /**
     * 查找用户对特定分类的行为统计
     */
    @Query("SELECT c.categoryId, COUNT(ub) as behaviorCount " +
           "FROM UserBehavior ub " +
           "JOIN Content c ON ub.contentId = c.id " +
           "WHERE ub.userId = :userId AND ub.createdAt >= :since " +
           "GROUP BY c.categoryId " +
           "ORDER BY behaviorCount DESC")
    List<Object[]> findUserCategoryPreferences(@Param("userId") Long userId, @Param("since") LocalDateTime since);

    /**
     * 查找用户的会话行为
     */
    List<UserBehavior> findByUserIdAndSessionIdOrderByCreatedAtAsc(Long userId, String sessionId);

    /**
     * 删除过期的行为数据
     */
    @Query("DELETE FROM UserBehavior ub WHERE ub.createdAt < :expireTime")
    int deleteExpiredBehaviors(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 查找设备类型统计
     */
    @Query("SELECT ub.deviceType, COUNT(ub) as count " +
           "FROM UserBehavior ub " +
           "WHERE ub.userId = :userId AND ub.createdAt >= :since " +
           "GROUP BY ub.deviceType " +
           "ORDER BY count DESC")
    List<Object[]> findDeviceTypeStatistics(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}