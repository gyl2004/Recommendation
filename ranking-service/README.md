# 智能内容推荐排序服务

基于Wide&Deep模型的内容排序服务，提供实时推荐排序和批量预测功能。

## 功能特性

- **Wide&Deep模型**: 结合线性模型的记忆能力和深度神经网络的泛化能力
- **特征工程管道**: 自动化的特征标准化、编码和转换
- **实时排序**: 毫秒级的候选内容排序服务
- **批量预测**: 高效的批量得分预测接口
- **特征缓存**: 基于Redis的实时特征存储和管理
- **模型服务**: 完整的模型训练、保存和加载流程
- **监控指标**: 性能统计和健康检查接口

## 技术架构

### 核心组件

1. **Wide&Deep模型** (`app/models/wide_deep_model.py`)
   - TensorFlow实现的Wide&Deep推荐模型
   - 支持多种特征类型：数值、分类、交叉、嵌入
   - 模型训练、推理和持久化

2. **特征工程管道** (`app/features/feature_pipeline.py`)
   - 特征标准化和归一化
   - 分类特征编码
   - 文本特征向量化
   - 实时特征处理

3. **排序服务** (`app/services/ranking_service.py`)
   - 候选内容排序
   - 批量预测
   - 特征管理
   - 性能监控

4. **API接口** (`app/api/ranking_api.py`)
   - RESTful API服务
   - 请求验证和响应格式化
   - 异步处理和错误处理

### 数据流程

```
用户请求 -> API网关 -> 排序服务 -> 特征获取 -> 模型预测 -> 结果排序 -> 返回响应
                                    ↓
                              Redis特征缓存
```

## 快速开始

### 环境要求

- Python 3.9+
- Redis 6.0+
- TensorFlow 2.13+

### 安装依赖

```bash
pip install -r requirements.txt
```

### 训练模型

```bash
# 使用示例数据训练模型
python scripts/train_model.py

# 使用自定义数据训练
python scripts/train_model.py --data_path /path/to/your/data.csv --epochs 20
```

### 启动服务

```bash
# 开发模式
python main.py

# 生产模式
uvicorn app.api.ranking_api:app --host 0.0.0.0 --port 8002
```

### Docker部署

```bash
# 构建镜像
docker build -t ranking-service .

# 运行容器
docker run -p 8002:8002 -e REDIS_URL=redis://your-redis:6379 ranking-service
```

## API文档

### 1. 内容排序

**POST** `/api/v1/ranking/rank`

对候选内容进行个性化排序。

**请求示例:**
```json
{
  "user_id": "user_123",
  "candidates": [
    {
      "content_id": "content_1",
      "content_type": "article",
      "title": "技术文章标题",
      "category": "tech"
    },
    {
      "content_id": "content_2",
      "content_type": "video",
      "title": "视频标题",
      "category": "entertainment"
    }
  ],
  "context": {
    "timestamp": 1640995200,
    "device_type": "mobile"
  },
  "max_results": 10
}
```

**响应示例:**
```json
{
  "user_id": "user_123",
  "ranked_items": [
    {
      "content_id": "content_2",
      "content_type": "video",
      "title": "视频标题",
      "category": "entertainment",
      "ranking_score": 0.85,
      "metadata": {}
    },
    {
      "content_id": "content_1",
      "content_type": "article",
      "title": "技术文章标题",
      "category": "tech",
      "ranking_score": 0.72,
      "metadata": {}
    }
  ],
  "total_candidates": 2,
  "processing_time_ms": 45.2,
  "timestamp": "2023-12-01T10:30:00"
}
```

### 2. 批量预测

**POST** `/api/v1/ranking/batch_predict`

批量预测内容得分。

**请求示例:**
```json
{
  "predictions": [
    {
      "features": {
        "user_age": 25,
        "user_gender": "M",
        "content_type": "article",
        "content_hot_score": 0.7
      }
    },
    {
      "features": {
        "user_age": 30,
        "user_gender": "F",
        "content_type": "video",
        "content_hot_score": 0.8
      }
    }
  ]
}
```

**响应示例:**
```json
{
  "scores": [0.72, 0.85],
  "total_requests": 2,
  "processing_time_ms": 12.5,
  "timestamp": "2023-12-01T10:30:00"
}
```

### 3. 特征更新

**POST** `/api/v1/ranking/features/update`

更新用户或内容特征。

**请求示例:**
```json
{
  "entity_type": "user",
  "entity_id": "user_123",
  "features": {
    "age": 25,
    "gender": "M",
    "interests": ["tech", "sports"],
    "activity_score": 0.8
  }
}
```

### 4. 健康检查

**GET** `/api/v1/ranking/health`

检查服务健康状态。

**响应示例:**
```json
{
  "status": "healthy",
  "redis_connected": true,
  "model_loaded": true,
  "pipeline_ready": true,
  "timestamp": "2023-12-01T10:30:00"
}
```

### 5. 统计信息

**GET** `/api/v1/ranking/stats`

获取服务性能统计。

**响应示例:**
```json
{
  "prediction_count": 1000,
  "total_prediction_time": 50.5,
  "avg_prediction_time": 0.0505,
  "model_loaded": true,
  "pipeline_fitted": true
}
```

## 模型训练

### 数据格式

训练数据应包含以下字段：

```csv
user_id,user_age,user_gender,user_activity_score,content_id,content_type,content_category,content_hot_score,label
user_1,25,M,0.8,content_1,article,tech,0.7,1
user_2,30,F,0.6,content_2,video,entertainment,0.9,0
```

### 特征配置

```python
feature_config = {
    'numeric_features': [
        'user_age',
        'user_activity_score',
        'content_hot_score'
    ],
    'categorical_features': [
        'user_gender',
        'content_type',
        'content_category'
    ],
    'text_features': [
        'content_title',
        'content_tags'
    ],
    'scaler_type': 'standard',
    'max_text_features': 1000
}
```

### 训练参数

```bash
python scripts/train_model.py \
    --data_path data/training_data.csv \
    --model_path models/wide_deep_model \
    --pipeline_path models/feature_pipeline.pkl \
    --epochs 20 \
    --batch_size 512
```

## 性能优化

### 缓存策略

- **用户特征缓存**: 1小时TTL
- **内容特征缓存**: 1小时TTL
- **预测结果缓存**: 可选，基于业务需求

### 批量处理

- **最大批次大小**: 1000个请求
- **超时设置**: 5秒
- **并发处理**: 异步I/O

### 模型优化

- **模型量化**: 减少模型大小和推理时间
- **特征选择**: 移除低重要性特征
- **模型蒸馏**: 使用小模型近似大模型

## 监控和告警

### 关键指标

- **响应时间**: P95 < 500ms
- **QPS**: 支持1000+ QPS
- **错误率**: < 0.1%
- **缓存命中率**: > 80%

### 告警规则

```yaml
# Prometheus告警规则
- alert: HighResponseTime
  expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m])) > 0.5
  for: 2m
  
- alert: LowCacheHitRate
  expr: rate(cache_hits_total[5m]) / rate(cache_requests_total[5m]) < 0.8
  for: 5m
```

## 测试

### 单元测试

```bash
pytest tests/ -v
```

### 集成测试

```bash
pytest tests/test_integration.py -v
```

### 性能测试

```bash
# 使用Apache Bench
ab -n 1000 -c 10 -H "Content-Type: application/json" \
   -p test_data.json http://localhost:8002/api/v1/ranking/rank
```

## 部署

### 环境变量

```bash
# 服务配置
HOST=0.0.0.0
PORT=8002

# Redis配置
REDIS_URL=redis://localhost:6379
REDIS_DB=0

# 模型配置
MODEL_PATH=models/wide_deep_model
PIPELINE_PATH=models/feature_pipeline.pkl

# 性能配置
MAX_CANDIDATES=1000
PREDICTION_TIMEOUT=5.0

# 日志配置
LOG_LEVEL=INFO
LOG_FILE=logs/ranking_service.log
```

### Docker Compose

```yaml
version: '3.8'
services:
  ranking-service:
    build: .
    ports:
      - "8002:8002"
    environment:
      - REDIS_URL=redis://redis:6379
    depends_on:
      - redis
    volumes:
      - ./models:/app/models
      - ./logs:/app/logs
  
  redis:
    image: redis:6.2-alpine
    ports:
      - "6379:6379"
```

## 故障排除

### 常见问题

1. **模型加载失败**
   - 检查模型文件路径
   - 验证TensorFlow版本兼容性
   - 查看错误日志

2. **Redis连接失败**
   - 检查Redis服务状态
   - 验证连接配置
   - 检查网络连通性

3. **预测性能差**
   - 检查特征缓存命中率
   - 优化模型结构
   - 调整批次大小

### 日志分析

```bash
# 查看服务日志
tail -f logs/ranking_service.log

# 查看错误日志
grep ERROR logs/ranking_service.log

# 查看性能日志
grep "processing_time" logs/ranking_service.log
```

## 贡献指南

1. Fork项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建Pull Request

## 许可证

MIT License