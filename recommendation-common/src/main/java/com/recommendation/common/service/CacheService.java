package com.recommendation.common.service;

import com.recommendation.common.constant.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis缓存服务类
 * 提供通用的缓存操作方法
 */
@Slf4j
@Service
public class CacheService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 设置缓存值
     */
    public void set(String key, Object value) {
        set(key, value, CacheConstants.DEFAULT_EXPIRE_TIME);
    }

    /**
     * 设置缓存值并指定过期时间
     */
    public void set(String key, Object value, long expireTime) {
        try {
            redisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.SECONDS);
            log.debug("Set cache: key={}, expireTime={}", key, expireTime);
        } catch (Exception e) {
            log.error("Failed to set cache: key={}", key, e);
        }
    }

    /**
     * 获取缓存值
     */
    public Object get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            log.debug("Get cache: key={}, found={}", key, value != null);
            return value;
        } catch (Exception e) {
            log.error("Failed to get cache: key={}", key, e);
            return null;
        }
    }

    /**
     * 获取缓存值并转换为指定类型
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = get(key);
        if (value != null && clazz.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    /**
     * 删除缓存
     */
    public boolean delete(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            log.debug("Delete cache: key={}, result={}", key, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to delete cache: key={}", key, e);
            return false;
        }
    }

    /**
     * 批量删除缓存
     */
    public long delete(Collection<String> keys) {
        try {
            Long result = redisTemplate.delete(keys);
            log.debug("Batch delete cache: keys={}, result={}", keys.size(), result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to batch delete cache: keys={}", keys.size(), e);
            return 0;
        }
    }

    /**
     * 检查缓存是否存在
     */
    public boolean exists(String key) {
        try {
            Boolean result = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to check cache existence: key={}", key, e);
            return false;
        }
    }

    /**
     * 设置缓存过期时间
     */
    public boolean expire(String key, long expireTime) {
        try {
            Boolean result = redisTemplate.expire(key, expireTime, TimeUnit.SECONDS);
            log.debug("Set expire time: key={}, expireTime={}, result={}", key, expireTime, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to set expire time: key={}", key, e);
            return false;
        }
    }

    /**
     * 获取缓存剩余过期时间
     */
    public long getExpire(String key) {
        try {
            Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return expire != null ? expire : -1;
        } catch (Exception e) {
            log.error("Failed to get expire time: key={}", key, e);
            return -1;
        }
    }

    /**
     * Hash操作 - 设置hash字段值
     */
    public void hSet(String key, String field, Object value) {
        try {
            redisTemplate.opsForHash().put(key, field, value);
            log.debug("Hash set: key={}, field={}", key, field);
        } catch (Exception e) {
            log.error("Failed to hash set: key={}, field={}", key, field, e);
        }
    }

    /**
     * Hash操作 - 获取hash字段值
     */
    public Object hGet(String key, String field) {
        try {
            Object value = redisTemplate.opsForHash().get(key, field);
            log.debug("Hash get: key={}, field={}, found={}", key, field, value != null);
            return value;
        } catch (Exception e) {
            log.error("Failed to hash get: key={}, field={}", key, field, e);
            return null;
        }
    }

    /**
     * Hash操作 - 获取所有hash字段
     */
    public Map<Object, Object> hGetAll(String key) {
        try {
            Map<Object, Object> result = redisTemplate.opsForHash().entries(key);
            log.debug("Hash get all: key={}, size={}", key, result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to hash get all: key={}", key, e);
            return new HashMap<>();
        }
    }

    /**
     * Hash操作 - 批量设置hash字段
     */
    public void hMSet(String key, Map<String, Object> map) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            log.debug("Hash multi set: key={}, fields={}", key, map.size());
        } catch (Exception e) {
            log.error("Failed to hash multi set: key={}", key, e);
        }
    }

    /**
     * Hash操作 - 删除hash字段
     */
    public boolean hDelete(String key, String... fields) {
        try {
            Long result = redisTemplate.opsForHash().delete(key, (Object[]) fields);
            log.debug("Hash delete: key={}, fields={}, result={}", key, Arrays.toString(fields), result);
            return result != null && result > 0;
        } catch (Exception e) {
            log.error("Failed to hash delete: key={}, fields={}", key, Arrays.toString(fields), e);
            return false;
        }
    }

    /**
     * List操作 - 左侧推入
     */
    public long lPush(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForList().leftPushAll(key, values);
            log.debug("List left push: key={}, count={}, result={}", key, values.length, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to list left push: key={}", key, e);
            return 0;
        }
    }

    /**
     * List操作 - 右侧推入
     */
    public long rPush(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForList().rightPushAll(key, values);
            log.debug("List right push: key={}, count={}, result={}", key, values.length, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to list right push: key={}", key, e);
            return 0;
        }
    }

    /**
     * List操作 - 获取范围内的元素
     */
    public List<Object> lRange(String key, long start, long end) {
        try {
            List<Object> result = redisTemplate.opsForList().range(key, start, end);
            log.debug("List range: key={}, start={}, end={}, size={}", key, start, end, 
                     result != null ? result.size() : 0);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to list range: key={}, start={}, end={}", key, start, end, e);
            return new ArrayList<>();
        }
    }

    /**
     * Set操作 - 添加元素
     */
    public long sAdd(String key, Object... values) {
        try {
            Long result = redisTemplate.opsForSet().add(key, values);
            log.debug("Set add: key={}, count={}, result={}", key, values.length, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to set add: key={}", key, e);
            return 0;
        }
    }

    /**
     * Set操作 - 获取所有元素
     */
    public Set<Object> sMembers(String key) {
        try {
            Set<Object> result = redisTemplate.opsForSet().members(key);
            log.debug("Set members: key={}, size={}", key, result != null ? result.size() : 0);
            return result != null ? result : new HashSet<>();
        } catch (Exception e) {
            log.error("Failed to set members: key={}", key, e);
            return new HashSet<>();
        }
    }

    /**
     * ZSet操作 - 添加元素
     */
    public boolean zAdd(String key, Object value, double score) {
        try {
            Boolean result = redisTemplate.opsForZSet().add(key, value, score);
            log.debug("ZSet add: key={}, value={}, score={}, result={}", key, value, score, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to zset add: key={}, value={}, score={}", key, value, score, e);
            return false;
        }
    }

    /**
     * ZSet操作 - 获取范围内的元素(按分数倒序)
     */
    public Set<Object> zRevRange(String key, long start, long end) {
        try {
            Set<Object> result = redisTemplate.opsForZSet().reverseRange(key, start, end);
            log.debug("ZSet reverse range: key={}, start={}, end={}, size={}", key, start, end,
                     result != null ? result.size() : 0);
            return result != null ? result : new LinkedHashSet<>();
        } catch (Exception e) {
            log.error("Failed to zset reverse range: key={}, start={}, end={}", key, start, end, e);
            return new LinkedHashSet<>();
        }
    }

    /**
     * 原子递增
     */
    public long increment(String key) {
        return increment(key, 1);
    }

    /**
     * 原子递增指定值
     */
    public long increment(String key, long delta) {
        try {
            Long result = redisTemplate.opsForValue().increment(key, delta);
            log.debug("Increment: key={}, delta={}, result={}", key, delta, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to increment: key={}, delta={}", key, delta, e);
            return 0;
        }
    }

    /**
     * 获取匹配模式的所有key
     */
    public Set<String> keys(String pattern) {
        try {
            Set<String> result = redisTemplate.keys(pattern);
            log.debug("Keys pattern: pattern={}, size={}", pattern, result != null ? result.size() : 0);
            return result != null ? result : new HashSet<>();
        } catch (Exception e) {
            log.error("Failed to get keys: pattern={}", pattern, e);
            return new HashSet<>();
        }
    }

    /**
     * 清空指定前缀的所有缓存
     */
    public long deleteByPattern(String pattern) {
        Set<String> keys = keys(pattern);
        if (!keys.isEmpty()) {
            return delete(keys);
        }
        return 0;
    }
}