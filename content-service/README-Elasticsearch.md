# Elasticsearch配置说明

## 概述

本模块实现了智能内容推荐系统的Elasticsearch搜索引擎配置，包括：

1. **Elasticsearch连接配置** - 配置ES客户端连接
2. **索引映射管理** - 创建和管理内容索引结构
3. **内容搜索API** - 提供多种搜索和相似度计算接口

## 核心组件

### 1. ElasticsearchConfig
- 配置Elasticsearch客户端连接
- 支持用户名密码认证
- 设置连接超时和socket超时

### 2. ElasticsearchService
- 索引管理（创建、删除索引）
- 文档操作（增删改查）
- 搜索功能（文本搜索、向量相似度搜索、热门内容）

### 3. ContentSearchService
- 实体与文档转换
- 集成内容管理与搜索功能
- 自动索引内容变更

### 4. ContentSearchController
- 提供RESTful搜索API
- 支持多种搜索模式
- 错误处理和响应格式化

## 索引结构

```json
{
  "mappings": {
    "properties": {
      "contentId": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {"type": "keyword"}
        }
      },
      "contentType": {"type": "keyword"},
      "tags": {"type": "keyword"},
      "category": {"type": "keyword"},
      "embedding": {
        "type": "dense_vector",
        "dims": 128
      },
      "publishTime": {"type": "date"},
      "hotScore": {"type": "float"},
      "authorId": {"type": "keyword"},
      "summary": {
        "type": "text",
        "analyzer": "standard"
      },
      "extraData": {"type": "object"},
      "createdAt": {"type": "date"},
      "updatedAt": {"type": "date"}
    }
  }
}
```

## API接口

### 1. 内容搜索
```
GET /api/v1/content/search?query=关键词&contentType=article&tags=技术,Java&page=0&size=10
```

### 2. 相似内容搜索
```
POST /api/v1/content/search/similar
Content-Type: application/json

[0.1, 0.2, 0.3, 0.4, 0.5]
```

### 3. 热门内容
```
GET /api/v1/content/search/hot?contentType=video&size=20
```

### 4. 内容推荐
```
GET /api/v1/content/search/{contentId}/recommendations?size=10
```

### 5. 多类型搜索
```
POST /api/v1/content/search/multi-type?query=测试&sizePerType=5
Content-Type: application/json

["article", "video", "product"]
```

## 配置参数

在 `application.yml` 中配置：

```yaml
elasticsearch:
  host: ${ES_HOST:localhost}
  port: ${ES_PORT:9200}
  username: ${ES_USERNAME:}
  password: ${ES_PASSWORD:}
```

## 功能特性

### 1. 自动索引管理
- 应用启动时自动创建索引
- 内容发布时自动索引
- 内容更新时自动更新索引
- 内容删除时自动删除索引

### 2. 多种搜索模式
- **文本搜索**: 基于标题和摘要的全文搜索
- **向量搜索**: 基于内容向量的相似度搜索
- **热门搜索**: 基于热度分数的排序
- **分类搜索**: 按内容类型和标签过滤

### 3. 热度分数计算
热度分数基于以下因素计算：
- 浏览次数 (权重: 0.1)
- 点赞次数 (权重: 2.0)
- 分享次数 (权重: 5.0)
- 评论次数 (权重: 3.0)
- 时间衰减因子
- 实体热度分数

### 4. 错误处理
- 索引操作失败不影响主业务流程
- 详细的错误日志记录
- 优雅的降级处理

## 测试

项目包含完整的单元测试和集成测试：

- `ElasticsearchServiceTest`: 测试ES服务功能
- `ContentSearchServiceTest`: 测试搜索服务功能
- `ContentSearchControllerTest`: 测试搜索API接口

## 部署要求

1. **Elasticsearch 7.x+**: 支持dense_vector类型
2. **Java 8+**: 支持高级REST客户端
3. **内存**: 建议4GB+用于ES实例
4. **存储**: 根据内容量规划索引存储空间

## 性能优化

1. **索引设置**: 3个分片，1个副本
2. **批量操作**: 支持批量索引提高性能
3. **缓存策略**: 结合Redis缓存热门搜索结果
4. **分页查询**: 避免深度分页影响性能

## 监控指标

建议监控以下指标：
- 索引大小和文档数量
- 搜索响应时间
- 索引操作成功率
- ES集群健康状态