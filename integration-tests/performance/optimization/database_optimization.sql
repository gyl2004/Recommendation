-- 智能内容推荐平台数据库性能优化脚本

-- =============================================
-- 索引优化
-- =============================================

-- 用户表索引优化
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_updated_at ON users(updated_at);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);

-- 内容表索引优化
CREATE INDEX idx_contents_type_status ON contents(content_type, status);
CREATE INDEX idx_contents_category_publish ON contents(category_id, publish_time);
CREATE INDEX idx_contents_author_type ON contents(author_id, content_type);
CREATE INDEX idx_contents_publish_time ON contents(publish_time DESC);
CREATE INDEX idx_contents_created_at ON contents(created_at DESC);

-- 用户行为表索引优化 (如果存在)
CREATE INDEX idx_user_behaviors_user_time ON user_behaviors(user_id, created_at DESC);
CREATE INDEX idx_user_behaviors_content_time ON user_behaviors(content_id, created_at DESC);
CREATE INDEX idx_user_behaviors_action_time ON user_behaviors(action_type, created_at DESC);

-- 分类表索引优化
CREATE INDEX idx_categories_parent_level ON categories(parent_id, level);
CREATE INDEX idx_categories_sort_order ON categories(sort_order);

-- =============================================
-- 查询优化
-- =============================================

-- 创建用户推荐查询的物化视图
CREATE VIEW user_content_stats AS
SELECT 
    u.id as user_id,
    u.username,
    COUNT(DISTINCT c.id) as content_count,
    COUNT(DISTINCT c.category_id) as category_count,
    MAX(c.publish_time) as latest_content_time
FROM users u
LEFT JOIN contents c ON u.id = c.author_id AND c.status = 'published'
GROUP BY u.id, u.username;

-- 创建热门内容查询视图
CREATE VIEW hot_contents AS
SELECT 
    c.*,
    cat.name as category_name,
    u.username as author_name
FROM contents c
LEFT JOIN categories cat ON c.category_id = cat.id
LEFT JOIN users u ON c.author_id = u.id
WHERE c.status = 'published'
AND c.publish_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
ORDER BY c.publish_time DESC;

-- =============================================
-- 数据库配置优化
-- =============================================

-- 连接池配置
SET GLOBAL max_connections = 1000;
SET GLOBAL max_user_connections = 800;
SET GLOBAL thread_cache_size = 100;
SET GLOBAL table_open_cache = 4000;
SET GLOBAL table_definition_cache = 2000;

-- InnoDB配置优化
SET GLOBAL innodb_buffer_pool_size = 8589934592; -- 8GB
SET GLOBAL innodb_buffer_pool_instances = 8;
SET GLOBAL innodb_log_file_size = 1073741824; -- 1GB
SET GLOBAL innodb_log_buffer_size = 67108864; -- 64MB
SET GLOBAL innodb_flush_log_at_trx_commit = 2;
SET GLOBAL innodb_flush_method = 'O_DIRECT';
SET GLOBAL innodb_io_capacity = 2000;
SET GLOBAL innodb_io_capacity_max = 4000;
SET GLOBAL innodb_read_io_threads = 8;
SET GLOBAL innodb_write_io_threads = 8;

-- 查询缓存配置
SET GLOBAL query_cache_type = 1;
SET GLOBAL query_cache_size = 268435456; -- 256MB
SET GLOBAL query_cache_limit = 2097152; -- 2MB

-- 临时表配置
SET GLOBAL tmp_table_size = 134217728; -- 128MB
SET GLOBAL max_heap_table_size = 134217728; -- 128MB

-- =============================================
-- 分区表优化 (针对大数据量场景)
-- =============================================

-- 用户行为数据分区表 (按月分区)
CREATE TABLE user_behaviors_partitioned (
    id BIGINT AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    content_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    session_id VARCHAR(64),
    device_type VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    duration INT DEFAULT 0,
    extra_data JSON,
    PRIMARY KEY (id, created_at),
    INDEX idx_user_time (user_id, created_at),
    INDEX idx_content_time (content_id, created_at),
    INDEX idx_action_time (action_type, created_at)
) PARTITION BY RANGE (YEAR(created_at) * 100 + MONTH(created_at)) (
    PARTITION p202401 VALUES LESS THAN (202402),
    PARTITION p202402 VALUES LESS THAN (202403),
    PARTITION p202403 VALUES LESS THAN (202404),
    PARTITION p202404 VALUES LESS THAN (202405),
    PARTITION p202405 VALUES LESS THAN (202406),
    PARTITION p202406 VALUES LESS THAN (202407),
    PARTITION p202407 VALUES LESS THAN (202408),
    PARTITION p202408 VALUES LESS THAN (202409),
    PARTITION p202409 VALUES LESS THAN (202410),
    PARTITION p202410 VALUES LESS THAN (202411),
    PARTITION p202411 VALUES LESS THAN (202412),
    PARTITION p202412 VALUES LESS THAN (202501),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- =============================================
-- 读写分离配置
-- =============================================

-- 创建只读用户
CREATE USER 'readonly'@'%' IDENTIFIED BY 'readonly_password';
GRANT SELECT ON recommendation_db.* TO 'readonly'@'%';
FLUSH PRIVILEGES;

-- 创建写入用户
CREATE USER 'writeuser'@'%' IDENTIFIED BY 'write_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON recommendation_db.* TO 'writeuser'@'%';
FLUSH PRIVILEGES;

-- =============================================
-- 性能监控查询
-- =============================================

-- 监控慢查询
SELECT 
    query_time,
    lock_time,
    rows_sent,
    rows_examined,
    sql_text
FROM mysql.slow_log
WHERE start_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
ORDER BY query_time DESC
LIMIT 10;

-- 监控表大小
SELECT 
    table_schema,
    table_name,
    ROUND(((data_length + index_length) / 1024 / 1024), 2) AS 'Size (MB)',
    table_rows
FROM information_schema.tables
WHERE table_schema = 'recommendation_db'
ORDER BY (data_length + index_length) DESC;

-- 监控索引使用情况
SELECT 
    t.table_schema,
    t.table_name,
    s.index_name,
    s.column_name,
    s.cardinality,
    ROUND(((s.cardinality / t.table_rows) * 100), 2) AS selectivity
FROM information_schema.statistics s
JOIN information_schema.tables t ON s.table_schema = t.table_schema 
    AND s.table_name = t.table_name
WHERE t.table_schema = 'recommendation_db'
    AND t.table_rows > 0
ORDER BY selectivity DESC;

-- 监控连接数
SELECT 
    SUBSTRING_INDEX(host, ':', 1) AS host_short,
    COUNT(*) AS connections,
    user
FROM information_schema.processlist
GROUP BY host_short, user
ORDER BY connections DESC;

-- =============================================
-- 数据清理和维护
-- =============================================

-- 清理过期的用户行为数据 (保留3个月)
DELETE FROM user_behaviors 
WHERE created_at < DATE_SUB(NOW(), INTERVAL 3 MONTH);

-- 优化表结构
OPTIMIZE TABLE users;
OPTIMIZE TABLE contents;
OPTIMIZE TABLE categories;
OPTIMIZE TABLE user_behaviors;

-- 分析表统计信息
ANALYZE TABLE users;
ANALYZE TABLE contents;
ANALYZE TABLE categories;
ANALYZE TABLE user_behaviors;

-- =============================================
-- 备份和恢复策略
-- =============================================

-- 创建备份脚本 (在shell中执行)
/*
#!/bin/bash
# backup_db.sh

DB_NAME="recommendation_db"
BACKUP_DIR="/backup/mysql"
DATE=$(date +%Y%m%d_%H%M%S)

# 创建备份目录
mkdir -p $BACKUP_DIR

# 全量备份
mysqldump --single-transaction --routines --triggers \
    --master-data=2 --databases $DB_NAME \
    > $BACKUP_DIR/${DB_NAME}_full_$DATE.sql

# 压缩备份文件
gzip $BACKUP_DIR/${DB_NAME}_full_$DATE.sql

# 清理7天前的备份
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete

echo "数据库备份完成: ${DB_NAME}_full_$DATE.sql.gz"
*/

-- =============================================
-- 性能测试专用配置
-- =============================================

-- 临时禁用二进制日志 (仅测试环境)
-- SET sql_log_bin = 0;

-- 临时禁用外键检查 (仅测试环境)
-- SET foreign_key_checks = 0;

-- 批量插入优化
SET SESSION bulk_insert_buffer_size = 268435456; -- 256MB
SET SESSION myisam_sort_buffer_size = 134217728; -- 128MB

-- 临时增加排序缓冲区
SET SESSION sort_buffer_size = 16777216; -- 16MB
SET SESSION read_buffer_size = 8388608; -- 8MB
SET SESSION read_rnd_buffer_size = 16777216; -- 16MB

-- =============================================
-- 监控和告警查询
-- =============================================

-- 检查锁等待
SELECT 
    r.trx_id waiting_trx_id,
    r.trx_mysql_thread_id waiting_thread,
    r.trx_query waiting_query,
    b.trx_id blocking_trx_id,
    b.trx_mysql_thread_id blocking_thread,
    b.trx_query blocking_query
FROM information_schema.innodb_lock_waits w
JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id
JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id;

-- 检查长时间运行的查询
SELECT 
    id,
    user,
    host,
    db,
    command,
    time,
    state,
    info
FROM information_schema.processlist
WHERE time > 30
    AND command != 'Sleep'
ORDER BY time DESC;