# 特征服务 (Feature Service)

智能内容推荐平台的特征服务，负责用户特征和内容特征的提取、存储、更新功能。

## 功能特性

- **用户特征管理**: 实时提取和更新用户画像特征
- **内容特征管理**: 批量计算和存储内容特征
- **多级存储**: Redis缓存 + ClickHouse持久化存储
- **实时处理**: 基于用户行为实时更新特征
- **批量操作**: 支持批量特征提取和更新
- **性能监控**: 缓存命中率和性能统计

## 技术栈

- **框架**: FastAPI + Uvicorn
- **缓存**: Redis (异步)
- **数据库**: ClickHouse
- **机器学习**: scikit-learn, numpy, pandas
- **日志**: Loguru
- **配置**: Pydantic Settings

## 快速开始

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 文件，配置数据库连接信息
```

### 3. 初始化数据库

```bash
python scripts/init_database.py
```

### 4. 启动服务

```bash
python main.py
```

服务将在 http://localhost:8003 启动

## API 接口

### 用户特征

- `GET /api/v1/features/user/{user_id}` - 获取用户特征
- `POST /api/v1/features/user/batch` - 批量获取用户特征

### 内容特征

- `GET /api/v1/features/content/{content_id}` - 获取内容特征
- `POST /api/v1/features/content/batch` - 批量获取内容特征

### 特征更新

- `POST /api/v1/features/update` - 更新特征
- `POST /api/v1/features/behavior` - 处理用户行为

### 系统管理

- `GET /api/v1/features/statistics` - 获取统计信息
- `POST /api/v1/features/cleanup` - 清理过期特征
- `POST /api/v1/features/backup` - 备份特征数据

## 数据模型

### 用户特征 (UserFeatures)

```python
{
    "user_id": "1001",
    "age_group": "25-34",
    "interests": ["tech", "sports"],
    "behavior_score": 8.5,
    "activity_level": "high",
    "preferred_content_types": ["article", "video"],
    "feature_vector": [0.1, 0.2, ...],
    "last_active": "2024-01-01T10:00:00",
    "created_at": "2024-01-01T09:00:00",
    "updated_at": "2024-01-01T10:00:00"
}
```

### 内容特征 (ContentFeatures)

```python
{
    "content_id": "2001",
    "content_type": "article",
    "title": "AI技术发展趋势",
    "category": "technology",
    "tags": ["ai", "tech", "future"],
    "quality_score": 9.2,
    "popularity_score": 7.8,
    "text_features": {"word_count": 1500, "title_length": 20},
    "embedding_vector": [0.3, 0.4, ...],
    "publish_time": "2024-01-01T08:00:00",
    "created_at": "2024-01-01T08:00:00",
    "updated_at": "2024-01-01T10:00:00"
}
```

## 部署

### Docker 部署

```bash
# 构建镜像
docker build -t feature-service .

# 运行容器
docker run -d \
  --name feature-service \
  -p 8003:8003 \
  -e REDIS_HOST=redis \
  -e CLICKHOUSE_HOST=clickhouse \
  feature-service
```

### Docker Compose

```yaml
version: '3.8'
services:
  feature-service:
    build: .
    ports:
      - "8003:8003"
    environment:
      - REDIS_HOST=redis
      - CLICKHOUSE_HOST=clickhouse
    depends_on:
      - redis
      - clickhouse
```

## 监控和维护

### 健康检查

```bash
curl http://localhost:8003/health
```

### 查看统计信息

```bash
curl http://localhost:8003/api/v1/features/statistics
```

### 清理过期数据

```bash
curl -X POST http://localhost:8003/api/v1/features/cleanup
```

## 性能优化

1. **缓存策略**: 用户特征缓存1小时，内容特征缓存2小时
2. **批量处理**: 支持批量获取和更新，提高吞吐量
3. **异步处理**: 使用后台任务处理耗时操作
4. **连接池**: Redis和ClickHouse使用连接池管理
5. **索引优化**: ClickHouse表使用合适的分区和索引策略

## 故障排除

### 常见问题

1. **Redis连接失败**: 检查Redis服务状态和连接配置
2. **ClickHouse连接失败**: 检查ClickHouse服务状态和权限配置
3. **特征计算慢**: 检查数据量和索引配置
4. **内存使用高**: 调整批处理大小和缓存过期时间

### 日志查看

```bash
tail -f logs/feature-service.log
```

## 开发指南

### 添加新特征

1. 在 `models/schemas.py` 中定义特征字段
2. 在相应的服务类中实现特征计算逻辑
3. 更新API接口和文档
4. 添加单元测试

### 性能测试

```bash
# 安装测试依赖
pip install pytest pytest-asyncio httpx

# 运行测试
pytest tests/
```