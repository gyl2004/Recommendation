package com.recommendation.common.constant;

/**
 * 缓存常量类
 * 定义Redis缓存的key前缀和过期时间
 */
public class CacheConstants {

    /**
     * 缓存key分隔符
     */
    public static final String CACHE_KEY_SEPARATOR = ":";

    /**
     * 默认缓存过期时间(秒) - 1小时
     */
    public static final long DEFAULT_EXPIRE_TIME = 3600L;

    /**
     * 短期缓存过期时间(秒) - 5分钟
     */
    public static final long SHORT_EXPIRE_TIME = 300L;

    /**
     * 长期缓存过期时间(秒) - 24小时
     */
    public static final long LONG_EXPIRE_TIME = 86400L;

    /**
     * 用户相关缓存key前缀
     */
    public static class User {
        public static final String PREFIX = "user";
        
        /**
         * 用户基础信息: user:info:{userId}
         */
        public static final String INFO = PREFIX + CACHE_KEY_SEPARATOR + "info";
        
        /**
         * 用户特征数据: user:features:{userId}
         */
        public static final String FEATURES = PREFIX + CACHE_KEY_SEPARATOR + "features";
        
        /**
         * 用户行为历史: user:behavior:{userId}
         */
        public static final String BEHAVIOR = PREFIX + CACHE_KEY_SEPARATOR + "behavior";
        
        /**
         * 用户偏好标签: user:preferences:{userId}
         */
        public static final String PREFERENCES = PREFIX + CACHE_KEY_SEPARATOR + "preferences";
        
        /**
         * 用户会话信息: user:session:{sessionId}
         */
        public static final String SESSION = PREFIX + CACHE_KEY_SEPARATOR + "session";
    }

    /**
     * 内容相关缓存key前缀
     */
    public static class Content {
        public static final String PREFIX = "content";
        
        /**
         * 内容基础信息: content:info:{contentId}
         */
        public static final String INFO = PREFIX + CACHE_KEY_SEPARATOR + "info";
        
        /**
         * 内容特征数据: content:features:{contentId}
         */
        public static final String FEATURES = PREFIX + CACHE_KEY_SEPARATOR + "features";
        
        /**
         * 热门内容列表: content:hot:{contentType}
         */
        public static final String HOT = PREFIX + CACHE_KEY_SEPARATOR + "hot";
        
        /**
         * 最新内容列表: content:latest:{contentType}
         */
        public static final String LATEST = PREFIX + CACHE_KEY_SEPARATOR + "latest";
        
        /**
         * 分类内容列表: content:category:{categoryId}
         */
        public static final String CATEGORY = PREFIX + CACHE_KEY_SEPARATOR + "category";
        
        /**
         * 内容统计信息: content:stats:{contentId}
         */
        public static final String STATS = PREFIX + CACHE_KEY_SEPARATOR + "stats";
    }

    /**
     * 推荐相关缓存key前缀
     */
    public static class Recommendation {
        public static final String PREFIX = "recommend";
        
        /**
         * 推荐结果: recommend:result:{userId}:{contentType}
         */
        public static final String RESULT = PREFIX + CACHE_KEY_SEPARATOR + "result";
        
        /**
         * 召回候选集: recommend:recall:{userId}:{algorithm}
         */
        public static final String RECALL = PREFIX + CACHE_KEY_SEPARATOR + "recall";
        
        /**
         * 排序结果: recommend:rank:{userId}:{algorithm}
         */
        public static final String RANK = PREFIX + CACHE_KEY_SEPARATOR + "rank";
        
        /**
         * 推荐历史: recommend:history:{userId}
         */
        public static final String HISTORY = PREFIX + CACHE_KEY_SEPARATOR + "history";
        
        /**
         * 算法模型缓存: recommend:model:{algorithmType}
         */
        public static final String MODEL = PREFIX + CACHE_KEY_SEPARATOR + "model";
    }

    /**
     * 分类相关缓存key前缀
     */
    public static class Category {
        public static final String PREFIX = "category";
        
        /**
         * 分类信息: category:info:{categoryId}
         */
        public static final String INFO = PREFIX + CACHE_KEY_SEPARATOR + "info";
        
        /**
         * 分类树结构: category:tree
         */
        public static final String TREE = PREFIX + CACHE_KEY_SEPARATOR + "tree";
        
        /**
         * 分类统计: category:stats:{categoryId}
         */
        public static final String STATS = PREFIX + CACHE_KEY_SEPARATOR + "stats";
    }

    /**
     * 系统相关缓存key前缀
     */
    public static class System {
        public static final String PREFIX = "system";
        
        /**
         * 系统配置: system:config:{configKey}
         */
        public static final String CONFIG = PREFIX + CACHE_KEY_SEPARATOR + "config";
        
        /**
         * 限流计数: system:ratelimit:{key}
         */
        public static final String RATE_LIMIT = PREFIX + CACHE_KEY_SEPARATOR + "ratelimit";
        
        /**
         * 分布式锁: system:lock:{lockKey}
         */
        public static final String LOCK = PREFIX + CACHE_KEY_SEPARATOR + "lock";
        
        /**
         * 统计计数: system:counter:{counterKey}
         */
        public static final String COUNTER = PREFIX + CACHE_KEY_SEPARATOR + "counter";
    }

    /**
     * 构建缓存key
     */
    public static String buildKey(String prefix, String... keys) {
        StringBuilder sb = new StringBuilder(prefix);
        for (String key : keys) {
            sb.append(CACHE_KEY_SEPARATOR).append(key);
        }
        return sb.toString();
    }

    /**
     * 构建用户特征缓存key
     */
    public static String buildUserFeaturesKey(Long userId) {
        return buildKey(User.FEATURES, String.valueOf(userId));
    }

    /**
     * 构建推荐结果缓存key
     */
    public static String buildRecommendationResultKey(Long userId, String contentType) {
        return buildKey(Recommendation.RESULT, String.valueOf(userId), contentType);
    }

    /**
     * 构建热门内容缓存key
     */
    public static String buildHotContentKey(String contentType) {
        return buildKey(Content.HOT, contentType);
    }

    /**
     * 构建内容特征缓存key
     */
    public static String buildContentFeaturesKey(Long contentId) {
        return buildKey(Content.FEATURES, String.valueOf(contentId));
    }
}