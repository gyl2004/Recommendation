-- 智能内容推荐平台 - 初始数据库表结构
-- Version: 1.0
-- Description: 创建用户表、内容表、分类表等基础表结构

-- 用户表
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    email VARCHAR(100) UNIQUE COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '手机号',
    profile_data JSON COMMENT '用户画像数据',
    status TINYINT DEFAULT 1 COMMENT '用户状态: 1-正常, 0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_phone (phone),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 分类表
CREATE TABLE categories (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '分类ID',
    name VARCHAR(50) NOT NULL COMMENT '分类名称',
    parent_id INT DEFAULT 0 COMMENT '父分类ID，0表示顶级分类',
    level TINYINT DEFAULT 1 COMMENT '分类层级',
    sort_order INT DEFAULT 0 COMMENT '排序权重',
    description VARCHAR(200) COMMENT '分类描述',
    status TINYINT DEFAULT 1 COMMENT '状态: 1-启用, 0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_parent_id (parent_id),
    INDEX idx_level (level),
    INDEX idx_sort_order (sort_order),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容分类表';

-- 内容表
CREATE TABLE contents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '内容ID',
    title VARCHAR(200) NOT NULL COMMENT '内容标题',
    content_type ENUM('article', 'video', 'product') NOT NULL COMMENT '内容类型',
    content_data JSON NOT NULL COMMENT '内容数据(根据类型存储不同字段)',
    tags JSON COMMENT '标签列表',
    category_id INT COMMENT '分类ID',
    author_id BIGINT COMMENT '作者ID',
    status ENUM('draft', 'published', 'archived') DEFAULT 'draft' COMMENT '内容状态',
    view_count BIGINT DEFAULT 0 COMMENT '浏览次数',
    like_count BIGINT DEFAULT 0 COMMENT '点赞次数',
    share_count BIGINT DEFAULT 0 COMMENT '分享次数',
    comment_count BIGINT DEFAULT 0 COMMENT '评论次数',
    hot_score DECIMAL(10,4) DEFAULT 0.0000 COMMENT '热度分数',
    publish_time TIMESTAMP NULL COMMENT '发布时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_type_status (content_type, status),
    INDEX idx_category (category_id),
    INDEX idx_author (author_id),
    INDEX idx_publish_time (publish_time),
    INDEX idx_hot_score (hot_score),
    INDEX idx_view_count (view_count),
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容表';

-- 用户行为表
CREATE TABLE user_behaviors (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '行为ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    content_id BIGINT NOT NULL COMMENT '内容ID',
    action_type ENUM('view', 'click', 'like', 'share', 'comment', 'collect') NOT NULL COMMENT '行为类型',
    session_id VARCHAR(64) COMMENT '会话ID',
    device_type VARCHAR(20) COMMENT '设备类型',
    duration INT DEFAULT 0 COMMENT '停留时长(秒)',
    extra_data JSON COMMENT '额外数据',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '行为时间',
    INDEX idx_user_id (user_id),
    INDEX idx_content_id (content_id),
    INDEX idx_action_type (action_type),
    INDEX idx_created_at (created_at),
    INDEX idx_user_content (user_id, content_id),
    INDEX idx_user_action_time (user_id, action_type, created_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (content_id) REFERENCES contents(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为表';

-- 推荐记录表
CREATE TABLE recommendation_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '推荐记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    content_ids JSON NOT NULL COMMENT '推荐的内容ID列表',
    algorithm_type VARCHAR(50) NOT NULL COMMENT '推荐算法类型',
    request_params JSON COMMENT '请求参数',
    response_time INT COMMENT '响应时间(毫秒)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '推荐时间',
    INDEX idx_user_id (user_id),
    INDEX idx_algorithm_type (algorithm_type),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='推荐记录表';