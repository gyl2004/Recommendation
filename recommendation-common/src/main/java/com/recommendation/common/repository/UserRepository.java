package com.recommendation.common.repository;

import com.recommendation.common.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户Repository接口
 * 提供用户相关的数据访问操作
 */
@Repository
public interface UserRepository extends BaseRepository<User, Long> {

    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查找用户
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据手机号查找用户
     */
    Optional<User> findByPhone(String phone);

    /**
     * 根据用户名或邮箱查找用户
     */
    Optional<User> findByUsernameOrEmail(String username, String email);

    /**
     * 查找所有激活的用户
     */
    List<User> findByStatus(Integer status);

    /**
     * 根据用户名模糊查询
     */
    List<User> findByUsernameContainingIgnoreCase(String username);

    /**
     * 查找指定时间范围内创建的用户
     */
    List<User> findByCreatedAtBetween(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 统计激活用户数量
     */
    long countByStatus(Integer status);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 检查手机号是否存在
     */
    boolean existsByPhone(String phone);

    /**
     * 查找最近活跃的用户(基于更新时间)
     */
    @Query("SELECT u FROM User u WHERE u.status = 1 AND u.updatedAt >= :since ORDER BY u.updatedAt DESC")
    List<User> findRecentActiveUsers(@Param("since") LocalDateTime since);

    /**
     * 根据用户画像中的属性查找用户
     */
    @Query(value = "SELECT * FROM users WHERE status = 1 AND JSON_EXTRACT(profile_data, :jsonPath) = :value", 
           nativeQuery = true)
    List<User> findByProfileAttribute(@Param("jsonPath") String jsonPath, @Param("value") String value);

    /**
     * 查找有特定兴趣标签的用户
     */
    @Query(value = "SELECT * FROM users WHERE status = 1 AND JSON_CONTAINS(JSON_EXTRACT(profile_data, '$.interests'), :interest)", 
           nativeQuery = true)
    List<User> findByInterest(@Param("interest") String interest);

    /**
     * 批量更新用户状态
     */
    @Query("UPDATE User u SET u.status = :status WHERE u.id IN :userIds")
    int updateStatusByIds(@Param("userIds") List<Long> userIds, @Param("status") Integer status);
}