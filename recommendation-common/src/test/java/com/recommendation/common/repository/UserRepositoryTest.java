package com.recommendation.common.repository;

import com.recommendation.common.config.TestConfig;
import com.recommendation.common.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserRepository测试类
 */
@DataJpaTest
@Import(TestConfig.class)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser1;
    private User testUser2;

    @BeforeEach
    void setUp() {
        // 创建测试用户1
        testUser1 = new User();
        testUser1.setUsername("testuser1");
        testUser1.setEmail("test1@example.com");
        testUser1.setPhone("13800138001");
        testUser1.setStatus(1);
        
        Map<String, Object> profileData1 = new HashMap<>();
        profileData1.put("age", 25);
        profileData1.put("gender", "male");
        profileData1.put("interests", List.of("tech", "sports"));
        testUser1.setProfileData(profileData1);

        // 创建测试用户2
        testUser2 = new User();
        testUser2.setUsername("testuser2");
        testUser2.setEmail("test2@example.com");
        testUser2.setPhone("13800138002");
        testUser2.setStatus(0); // 禁用状态
        
        Map<String, Object> profileData2 = new HashMap<>();
        profileData2.put("age", 30);
        profileData2.put("gender", "female");
        profileData2.put("interests", List.of("entertainment", "lifestyle"));
        testUser2.setProfileData(profileData2);

        // 保存测试数据
        entityManager.persistAndFlush(testUser1);
        entityManager.persistAndFlush(testUser2);
    }

    @Test
    void testFindByUsername() {
        // 测试根据用户名查找用户
        Optional<User> found = userRepository.findByUsername("testuser1");
        
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser1");
        assertThat(found.get().getEmail()).isEqualTo("test1@example.com");
    }

    @Test
    void testFindByEmail() {
        // 测试根据邮箱查找用户
        Optional<User> found = userRepository.findByEmail("test1@example.com");
        
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser1");
    }

    @Test
    void testFindByPhone() {
        // 测试根据手机号查找用户
        Optional<User> found = userRepository.findByPhone("13800138001");
        
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser1");
    }

    @Test
    void testFindByUsernameOrEmail() {
        // 测试根据用户名或邮箱查找用户
        Optional<User> foundByUsername = userRepository.findByUsernameOrEmail("testuser1", "nonexistent@example.com");
        Optional<User> foundByEmail = userRepository.findByUsernameOrEmail("nonexistent", "test1@example.com");
        
        assertThat(foundByUsername).isPresent();
        assertThat(foundByEmail).isPresent();
        assertThat(foundByUsername.get().getId()).isEqualTo(foundByEmail.get().getId());
    }

    @Test
    void testFindByStatus() {
        // 测试根据状态查找用户
        List<User> activeUsers = userRepository.findByStatus(1);
        List<User> inactiveUsers = userRepository.findByStatus(0);
        
        assertThat(activeUsers).hasSize(1);
        assertThat(activeUsers.get(0).getUsername()).isEqualTo("testuser1");
        
        assertThat(inactiveUsers).hasSize(1);
        assertThat(inactiveUsers.get(0).getUsername()).isEqualTo("testuser2");
    }

    @Test
    void testFindByUsernameContainingIgnoreCase() {
        // 测试用户名模糊查询
        List<User> users = userRepository.findByUsernameContainingIgnoreCase("testuser");
        
        assertThat(users).hasSize(2);
    }

    @Test
    void testCountByStatus() {
        // 测试统计激活用户数量
        long activeCount = userRepository.countByStatus(1);
        long inactiveCount = userRepository.countByStatus(0);
        
        assertThat(activeCount).isEqualTo(1);
        assertThat(inactiveCount).isEqualTo(1);
    }

    @Test
    void testExistsByUsername() {
        // 测试检查用户名是否存在
        boolean exists = userRepository.existsByUsername("testuser1");
        boolean notExists = userRepository.existsByUsername("nonexistent");
        
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void testExistsByEmail() {
        // 测试检查邮箱是否存在
        boolean exists = userRepository.existsByEmail("test1@example.com");
        boolean notExists = userRepository.existsByEmail("nonexistent@example.com");
        
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void testExistsByPhone() {
        // 测试检查手机号是否存在
        boolean exists = userRepository.existsByPhone("13800138001");
        boolean notExists = userRepository.existsByPhone("13800138999");
        
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    void testFindRecentActiveUsers() {
        // 测试查找最近活跃的用户
        LocalDateTime since = LocalDateTime.now().minusHours(1);
        List<User> recentUsers = userRepository.findRecentActiveUsers(since);
        
        // 只有激活状态的用户会被返回
        assertThat(recentUsers).hasSize(1);
        assertThat(recentUsers.get(0).getUsername()).isEqualTo("testuser1");
    }

    @Test
    void testFindByCreatedAtBetween() {
        // 测试查找指定时间范围内创建的用户
        LocalDateTime start = LocalDateTime.now().minusHours(1);
        LocalDateTime end = LocalDateTime.now().plusHours(1);
        
        List<User> users = userRepository.findByCreatedAtBetween(start, end);
        
        assertThat(users).hasSize(2);
    }

    @Test
    void testUserEntityMethods() {
        // 测试User实体类的方法
        assertThat(testUser1.isActive()).isTrue();
        assertThat(testUser2.isActive()).isFalse();
        
        // 测试画像属性操作
        testUser1.setProfileAttribute("newKey", "newValue");
        assertThat(testUser1.getProfileAttribute("newKey")).isEqualTo("newValue");
        assertThat(testUser1.getProfileAttribute("age")).isEqualTo(25);
    }
}