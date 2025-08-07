package com.recommendation.service.health;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuator.health.Health;
import org.springframework.boot.actuator.health.Status;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 健康检查服务测试
 */
@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private Connection connection;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        healthCheckService = new HealthCheckService(dataSource, redisTemplate);
    }

    @Test
    void testHealthCheckAllHealthy() throws SQLException {
        // 模拟数据库健康
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        // 模拟Redis健康
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("test");
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any());
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // 执行健康检查
        Health health = healthCheckService.health();

        // 验证结果
        assertEquals(Status.UP, health.getStatus());
        assertEquals("UP", health.getDetails().get("database"));
        assertEquals("UP", health.getDetails().get("redis"));
        assertEquals("UP", health.getDetails().get("system"));
    }

    @Test
    void testHealthCheckDatabaseUnhealthy() throws SQLException {
        // 模拟数据库不健康
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(false);

        // 模拟Redis健康
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("test");
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any());
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // 执行健康检查
        Health health = healthCheckService.health();

        // 验证结果
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("DOWN", health.getDetails().get("database"));
        assertEquals("UP", health.getDetails().get("redis"));
        assertEquals("UP", health.getDetails().get("system"));
    }

    @Test
    void testHealthCheckRedisUnhealthy() throws SQLException {
        // 模拟数据库健康
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        // 模拟Redis不健康
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis connection failed"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any());

        // 执行健康检查
        Health health = healthCheckService.health();

        // 验证结果
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("UP", health.getDetails().get("database"));
        assertEquals("DOWN", health.getDetails().get("redis"));
        assertEquals("UP", health.getDetails().get("system"));
    }

    @Test
    void testCheckDatabaseHealthSuccess() throws SQLException {
        // 模拟数据库连接成功
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        // 执行数据库健康检查
        boolean result = healthCheckService.checkDatabaseHealth();

        // 验证结果
        assertTrue(result);
        verify(dataSource).getConnection();
        verify(connection).isValid(anyInt());
        verify(connection).close();
    }

    @Test
    void testCheckDatabaseHealthFailure() throws SQLException {
        // 模拟数据库连接失败
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        // 执行数据库健康检查
        boolean result = healthCheckService.checkDatabaseHealth();

        // 验证结果
        assertFalse(result);
        verify(dataSource).getConnection();
    }

    @Test
    void testCheckRedisHealthSuccess() {
        // 模拟Redis操作成功
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("test");
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any());
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // 执行Redis健康检查
        boolean result = healthCheckService.checkRedisHealth();

        // 验证结果
        assertTrue(result);
        verify(redisTemplate).opsForValue();
        verify(valueOperations).set(anyString(), eq("test"), anyLong(), any());
        verify(valueOperations).get(anyString());
        verify(redisTemplate).delete(anyString());
    }

    @Test
    void testCheckRedisHealthFailure() {
        // 模拟Redis操作失败
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any());

        // 执行Redis健康检查
        boolean result = healthCheckService.checkRedisHealth();

        // 验证结果
        assertFalse(result);
        verify(redisTemplate).opsForValue();
        verify(valueOperations).set(anyString(), eq("test"), anyLong(), any());
    }

    @Test
    void testCheckSystemHealthSuccess() {
        // 系统健康检查主要检查内存使用率
        // 在正常情况下应该返回true（除非内存使用率超过90%）
        boolean result = healthCheckService.checkSystemHealth();

        // 验证结果（通常应该是健康的）
        assertTrue(result);
    }

    @Test
    void testGetDetailedHealthStatus() throws SQLException {
        // 模拟所有组件健康
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(anyInt())).thenReturn(true);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("test");
        doNothing().when(valueOperations).set(anyString(), anyString(), anyLong(), any());
        when(redisTemplate.delete(anyString())).thenReturn(true);

        // 执行详细健康状态检查
        HealthStatus status = healthCheckService.getDetailedHealthStatus();

        // 验证结果
        assertNotNull(status);
        assertTrue(status.isOverall());
        assertTrue(status.isDatabase());
        assertTrue(status.isRedis());
        assertTrue(status.isSystem());
        assertTrue(status.getTotalMemory() > 0);
        assertTrue(status.getUsedMemory() >= 0);
        assertTrue(status.getFreeMemory() >= 0);
        assertTrue(status.getAvailableProcessors() > 0);
        assertTrue(status.getTimestamp() > 0);
        assertTrue(status.getMemoryUsageRatio() >= 0 && status.getMemoryUsageRatio() <= 1);
        assertTrue(status.getMemoryUsagePercentage() >= 0 && status.getMemoryUsagePercentage() <= 100);
    }

    @Test
    void testGetDetailedHealthStatusWithFailures() throws SQLException {
        // 模拟数据库不健康
        when(dataSource.getConnection()).thenThrow(new SQLException("DB error"));

        // 模拟Redis不健康
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any());

        // 执行详细健康状态检查
        HealthStatus status = healthCheckService.getDetailedHealthStatus();

        // 验证结果
        assertNotNull(status);
        assertFalse(status.isOverall());
        assertFalse(status.isDatabase());
        assertFalse(status.isRedis());
        // 系统健康检查通常会成功
        assertTrue(status.isSystem());
    }
}