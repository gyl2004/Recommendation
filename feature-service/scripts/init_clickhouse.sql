-- ClickHouse数据库初始化脚本
-- 创建推荐系统相关的表结构

-- 创建数据库
CREATE DATABASE IF NOT EXISTS recommendation;

USE recommendation;

-- 用户行为数据表
CREATE TABLE IF NOT EXISTS user_behaviors (
    user_id UInt64,
    content_id UInt64,
    action_type String,
    content_type String,
    session_id String,
    device_type String,
    timestamp DateTime,
    duration UInt32,
    extra_data String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (user_id, timestamp)
SETTINGS index_granularity = 8192;

-- 特征向量存储表
CREATE TABLE IF NOT EXISTS feature_vectors (
    entity_id UInt64,
    entity_type String,  -- 'user' or 'content'
    feature_vector Array(Float32),
    created_at DateTime
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_at)
ORDER BY (entity_type, entity_id, created_at)
SETTINGS index_granularity = 8192;

-- 特征备份表
CREATE TABLE IF NOT EXISTS feature_backups (
    entity_id UInt64,
    feature_type String,  -- 'user_features' or 'content_features'
    feature_data String,
    backup_time DateTime
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(backup_time)
ORDER BY (feature_type, entity_id, backup_time)
SETTINGS index_granularity = 8192;

-- 用户画像统计表
CREATE TABLE IF NOT EXISTS user_profile_stats (
    user_id UInt64,
    stat_date Date,
    total_actions UInt32,
    view_count UInt32,
    click_count UInt32,
    like_count UInt32,
    share_count UInt32,
    comment_count UInt32,
    purchase_count UInt32,
    session_count UInt32,
    avg_session_duration Float32,
    preferred_content_type String,
    activity_score Float32
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (user_id, stat_date)
SETTINGS index_granularity = 8192;

-- 内容统计表
CREATE TABLE IF NOT EXISTS content_stats (
    content_id UInt64,
    stat_date Date,
    view_count UInt32,
    click_count UInt32,
    like_count UInt32,
    share_count UInt32,
    comment_count UInt32,
    unique_users UInt32,
    avg_view_duration Float32,
    bounce_rate Float32,
    quality_score Float32,
    popularity_score Float32
) ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(stat_date)
ORDER BY (content_id, stat_date)
SETTINGS index_granularity = 8192;

-- 实时特征计算的物化视图
CREATE MATERIALIZED VIEW IF NOT EXISTS user_realtime_features_mv
TO user_profile_stats
AS SELECT
    user_id,
    toDate(timestamp) as stat_date,
    count() as total_actions,
    countIf(action_type = 'view') as view_count,
    countIf(action_type = 'click') as click_count,
    countIf(action_type = 'like') as like_count,
    countIf(action_type = 'share') as share_count,
    countIf(action_type = 'comment') as comment_count,
    countIf(action_type = 'purchase') as purchase_count,
    uniq(session_id) as session_count,
    avg(duration) as avg_session_duration,
    any(content_type) as preferred_content_type,
    sum(multiIf(
        action_type = 'view', 1,
        action_type = 'click', 2,
        action_type = 'like', 3,
        action_type = 'share', 4,
        action_type = 'comment', 3.5,
        action_type = 'purchase', 5,
        0
    )) / 10.0 as activity_score
FROM user_behaviors
GROUP BY user_id, toDate(timestamp);

-- 内容实时统计的物化视图
CREATE MATERIALIZED VIEW IF NOT EXISTS content_realtime_stats_mv
TO content_stats
AS SELECT
    content_id,
    toDate(timestamp) as stat_date,
    countIf(action_type = 'view') as view_count,
    countIf(action_type = 'click') as click_count,
    countIf(action_type = 'like') as like_count,
    countIf(action_type = 'share') as share_count,
    countIf(action_type = 'comment') as comment_count,
    uniq(user_id) as unique_users,
    avgIf(duration, action_type = 'view') as avg_view_duration,
    countIf(duration < 10) / count() as bounce_rate,
    0.0 as quality_score,  -- 需要单独计算
    (countIf(action_type = 'view') * 1 + 
     countIf(action_type = 'like') * 3 + 
     countIf(action_type = 'share') * 5) / 100.0 as popularity_score
FROM user_behaviors
GROUP BY content_id, toDate(timestamp);

-- 创建索引以提高查询性能
-- 用户行为表的跳数索引
ALTER TABLE user_behaviors ADD INDEX idx_user_action user_id TYPE minmax GRANULARITY 3;
ALTER TABLE user_behaviors ADD INDEX idx_content_action content_id TYPE minmax GRANULARITY 3;
ALTER TABLE user_behaviors ADD INDEX idx_action_type action_type TYPE set(100) GRANULARITY 1;

-- 特征向量表的索引
ALTER TABLE feature_vectors ADD INDEX idx_entity_type entity_type TYPE set(10) GRANULARITY 1;

-- 创建用于特征计算的函数
-- 计算用户兴趣向量的函数（需要在ClickHouse中创建UDF）