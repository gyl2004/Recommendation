-- 智能内容推荐平台数据库初始化脚本
-- 用于开发环境快速搭建数据库

-- 创建数据库
CREATE DATABASE IF NOT EXISTS recommendation_platform 
    CHARACTER SET utf8mb4 
    COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE recommendation_platform;

-- 创建数据库用户(如果不存在)
CREATE USER IF NOT EXISTS 'recommendation_user'@'%' IDENTIFIED BY 'recommendation_pass';

-- 授权
GRANT ALL PRIVILEGES ON recommendation_platform.* TO 'recommendation_user'@'%';

-- 刷新权限
FLUSH PRIVILEGES;

-- 插入初始分类数据
INSERT INTO categories (id, name, parent_id, level, sort_order, description, status) VALUES
(1, '科技', 0, 1, 1, '科技相关内容', 1),
(2, '娱乐', 0, 1, 2, '娱乐相关内容', 1),
(3, '体育', 0, 1, 3, '体育相关内容', 1),
(4, '财经', 0, 1, 4, '财经相关内容', 1),
(5, '生活', 0, 1, 5, '生活相关内容', 1),
(6, '人工智能', 1, 2, 1, 'AI技术相关', 1),
(7, '移动开发', 1, 2, 2, '移动应用开发', 1),
(8, '电影', 2, 2, 1, '电影相关内容', 1),
(9, '音乐', 2, 2, 2, '音乐相关内容', 1),
(10, '足球', 3, 2, 1, '足球相关内容', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 插入测试用户数据
INSERT INTO users (id, username, email, phone, profile_data, status) VALUES
(1, 'test_user_1', 'user1@example.com', '13800138001', '{"age": 25, "gender": "male", "interests": ["tech", "sports"]}', 1),
(2, 'test_user_2', 'user2@example.com', '13800138002', '{"age": 30, "gender": "female", "interests": ["entertainment", "lifestyle"]}', 1),
(3, 'test_user_3', 'user3@example.com', '13800138003', '{"age": 28, "gender": "male", "interests": ["finance", "tech"]}', 1)
ON DUPLICATE KEY UPDATE username = VALUES(username);

-- 插入测试内容数据
INSERT INTO contents (id, title, content_type, content_data, tags, category_id, author_id, status, view_count, like_count, hot_score, publish_time) VALUES
(1, '人工智能的未来发展趋势', 'article', '{"content": "人工智能技术正在快速发展...", "word_count": 1500, "reading_time": 5}', '["AI", "技术", "未来"]', 6, 1, 'published', 1250, 89, 85.6, NOW()),
(2, '最新手机评测视频', 'video', '{"video_url": "https://example.com/video1.mp4", "duration": 600, "resolution": "1080p", "thumbnail": "https://example.com/thumb1.jpg"}', '["手机", "评测", "科技"]', 7, 2, 'published', 3200, 156, 92.3, NOW()),
(3, '智能手表推荐', 'product', '{"price": 1999, "brand": "Apple", "model": "Watch Series 8", "images": ["https://example.com/watch1.jpg"], "specs": {"display": "45mm", "battery": "18h"}}', '["智能手表", "Apple", "推荐"]', 1, 1, 'published', 890, 67, 78.9, NOW())
ON DUPLICATE KEY UPDATE title = VALUES(title);