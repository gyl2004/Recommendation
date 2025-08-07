package com.recommendation.common.service;

import com.recommendation.common.config.TestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CacheService测试类
 * 使用内存Redis进行测试
 */
@SpringBootTest
@Import(TestConfig.class)
@ActiveProfiles("test")
class CacheServiceTest {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final String TEST_KEY = "test:key";
    private final String TEST_VALUE = "test_value";

    @BeforeEach
    void setUp() {
        // 清空测试数据
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    void testSetAndGet() {
        // 测试设置和获取缓存
        cacheService.set(TEST_KEY, TEST_VALUE);
        
        Object result = cacheService.get(TEST_KEY);
        assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    void testSetWithExpireTime() {
        // 测试设置带过期时间的缓存
        cacheService.set(TEST_KEY, TEST_VALUE, 10);
        
        Object result = cacheService.get(TEST_KEY);
        assertThat(result).isEqualTo(TEST_VALUE);
        
        long expireTime = cacheService.getExpire(TEST_KEY);
        assertThat(expireTime).isGreaterThan(0).isLessThanOrEqualTo(10);
    }

    @Test
    void testGetWithClass() {
        // 测试类型转换获取
        cacheService.set(TEST_KEY, TEST_VALUE);
        
        String result = cacheService.get(TEST_KEY, String.class);
        assertThat(result).isEqualTo(TEST_VALUE);
    }

    @Test
    void testDelete() {
        // 测试删除缓存
        cacheService.set(TEST_KEY, TEST_VALUE);
        assertThat(cacheService.exists(TEST_KEY)).isTrue();
        
        boolean deleted = cacheService.delete(TEST_KEY);
        assertThat(deleted).isTrue();
        assertThat(cacheService.exists(TEST_KEY)).isFalse();
    }

    @Test
    void testBatchDelete() {
        // 测试批量删除
        List<String> keys = Arrays.asList("test:key1", "test:key2", "test:key3");
        for (String key : keys) {
            cacheService.set(key, "value");
        }
        
        long deletedCount = cacheService.delete(keys);
        assertThat(deletedCount).isEqualTo(3);
        
        for (String key : keys) {
            assertThat(cacheService.exists(key)).isFalse();
        }
    }

    @Test
    void testHashOperations() {
        // 测试Hash操作
        String hashKey = "test:hash";
        String field1 = "field1";
        String field2 = "field2";
        String value1 = "value1";
        String value2 = "value2";
        
        // 设置hash字段
        cacheService.hSet(hashKey, field1, value1);
        cacheService.hSet(hashKey, field2, value2);
        
        // 获取hash字段
        Object result1 = cacheService.hGet(hashKey, field1);
        Object result2 = cacheService.hGet(hashKey, field2);
        assertThat(result1).isEqualTo(value1);
        assertThat(result2).isEqualTo(value2);
        
        // 获取所有hash字段
        Map<Object, Object> allFields = cacheService.hGetAll(hashKey);
        assertThat(allFields).hasSize(2);
        assertThat(allFields.get(field1)).isEqualTo(value1);
        assertThat(allFields.get(field2)).isEqualTo(value2);
        
        // 批量设置hash字段
        Map<String, Object> newFields = new HashMap<>();
        newFields.put("field3", "value3");
        newFields.put("field4", "value4");
        cacheService.hMSet(hashKey, newFields);
        
        Map<Object, Object> updatedFields = cacheService.hGetAll(hashKey);
        assertThat(updatedFields).hasSize(4);
        
        // 删除hash字段
        boolean deleted = cacheService.hDelete(hashKey, field1, field2);
        assertThat(deleted).isTrue();
        
        Map<Object, Object> remainingFields = cacheService.hGetAll(hashKey);
        assertThat(remainingFields).hasSize(2);
    }

    @Test
    void testListOperations() {
        // 测试List操作
        String listKey = "test:list";
        String[] values = {"value1", "value2", "value3"};
        
        // 左侧推入
        long leftPushResult = cacheService.lPush(listKey, (Object[]) values);
        assertThat(leftPushResult).isEqualTo(3);
        
        // 获取范围内的元素
        List<Object> rangeResult = cacheService.lRange(listKey, 0, -1);
        assertThat(rangeResult).hasSize(3);
        
        // 右侧推入
        String newValue = "value4";
        long rightPushResult = cacheService.rPush(listKey, newValue);
        assertThat(rightPushResult).isEqualTo(4);
        
        List<Object> updatedRange = cacheService.lRange(listKey, 0, -1);
        assertThat(updatedRange).hasSize(4);
    }

    @Test
    void testSetOperations() {
        // 测试Set操作
        String setKey = "test:set";
        String[] values = {"value1", "value2", "value3", "value1"}; // 包含重复值
        
        // 添加元素
        long addResult = cacheService.sAdd(setKey, (Object[]) values);
        assertThat(addResult).isEqualTo(3); // Set会去重
        
        // 获取所有元素
        Set<Object> members = cacheService.sMembers(setKey);
        assertThat(members).hasSize(3);
        assertThat(members).contains("value1", "value2", "value3");
    }

    @Test
    void testZSetOperations() {
        // 测试ZSet操作
        String zsetKey = "test:zset";
        
        // 添加元素
        boolean added1 = cacheService.zAdd(zsetKey, "member1", 1.0);
        boolean added2 = cacheService.zAdd(zsetKey, "member2", 2.0);
        boolean added3 = cacheService.zAdd(zsetKey, "member3", 3.0);
        
        assertThat(added1).isTrue();
        assertThat(added2).isTrue();
        assertThat(added3).isTrue();
        
        // 获取范围内的元素(按分数倒序)
        Set<Object> reverseRange = cacheService.zRevRange(zsetKey, 0, -1);
        assertThat(reverseRange).hasSize(3);
        
        // 验证顺序(分数高的在前)
        List<Object> orderedList = new ArrayList<>(reverseRange);
        assertThat(orderedList.get(0)).isEqualTo("member3");
        assertThat(orderedList.get(1)).isEqualTo("member2");
        assertThat(orderedList.get(2)).isEqualTo("member1");
    }

    @Test
    void testIncrement() {
        // 测试原子递增
        String counterKey = "test:counter";
        
        // 递增1
        long result1 = cacheService.increment(counterKey);
        assertThat(result1).isEqualTo(1);
        
        // 递增指定值
        long result2 = cacheService.increment(counterKey, 5);
        assertThat(result2).isEqualTo(6);
        
        // 再次递增1
        long result3 = cacheService.increment(counterKey);
        assertThat(result3).isEqualTo(7);
    }

    @Test
    void testKeys() {
        // 测试获取匹配模式的key
        cacheService.set("test:key1", "value1");
        cacheService.set("test:key2", "value2");
        cacheService.set("other:key", "value3");
        
        Set<String> testKeys = cacheService.keys("test:*");
        assertThat(testKeys).hasSize(2);
        assertThat(testKeys).contains("test:key1", "test:key2");
    }

    @Test
    void testDeleteByPattern() {
        // 测试按模式删除
        cacheService.set("test:key1", "value1");
        cacheService.set("test:key2", "value2");
        cacheService.set("other:key", "value3");
        
        long deletedCount = cacheService.deleteByPattern("test:*");
        assertThat(deletedCount).isEqualTo(2);
        
        assertThat(cacheService.exists("test:key1")).isFalse();
        assertThat(cacheService.exists("test:key2")).isFalse();
        assertThat(cacheService.exists("other:key")).isTrue();
    }

    @Test
    void testExpire() {
        // 测试设置过期时间
        cacheService.set(TEST_KEY, TEST_VALUE);
        
        boolean expireResult = cacheService.expire(TEST_KEY, 10);
        assertThat(expireResult).isTrue();
        
        long expireTime = cacheService.getExpire(TEST_KEY);
        assertThat(expireTime).isGreaterThan(0).isLessThanOrEqualTo(10);
    }
}