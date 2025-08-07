package com.recommendation.user.repository;

import com.recommendation.user.entity.UserBehaviorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户行为Repository接口
 */
@Repository
public interface UserBehaviorEntityRepository extends JpaRepository<UserBehaviorEntity, Long> {

    /**
     * 根据用户ID查找行为记录
     */
    List<UserBehaviorEntity> findByUserId(Long userId);

    /**
     * 根据用户ID和时间范围查找行为记录
     */
    List<UserBehaviorEntity> findByUserIdAndTimestampBetween(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 根据内容ID查找行为记录
     */
    List<UserBehaviorEntity> findByContentId(Long contentId);

    /**
     * 根据行为类型查找记录
     */
    List<UserBehaviorEntity> findByActionType(String actionType);

    /**
     * 根据用户ID和行为类型查找记录
     */
    List<UserBehaviorEntity> findByUserIdAndActionType(Long userId, String actionType);

    /**
     * 查找用户最近的行为记录
     */
    @Query("SELECT b FROM UserBehaviorEntity b WHERE b.userId = :userId ORDER BY b.timestamp DESC")
    List<UserBehaviorEntity> findRecentBehaviorsByUserId(@Param("userId") Long userId);

    /**
     * 统计用户在指定时间范围内的行为数量
     */
    @Query("SELECT COUNT(b) FROM UserBehaviorEntity b WHERE b.userId = :userId AND b.timestamp BETWEEN :startTime AND :endTime")
    long countByUserIdAndTimestampBetween(@Param("userId") Long userId, 
                                         @Param("startTime") LocalDateTime startTime, 
                                         @Param("endTime") LocalDateTime endTime);

    /**
     * 查找热门内容（基于行为数量）
     */
    @Query("SELECT b.contentId, COUNT(b) as behaviorCount FROM UserBehaviorEntity b " +
           "WHERE b.timestamp >= :since GROUP BY b.contentId ORDER BY behaviorCount DESC")
    List<Object[]> findPopularContentSince(@Param("since") LocalDateTime since);
}