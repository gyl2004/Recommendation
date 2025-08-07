# 智能内容推荐平台 API 文档

## 概述

智能内容推荐平台提供RESTful API接口，支持用户管理、内容管理、推荐服务等核心功能。

## 基础信息

- **Base URL**: `https://api.recommendation.com/api/v1`
- **认证方式**: Bearer Token
- **数据格式**: JSON
- **字符编码**: UTF-8

## 通用响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "timestamp": "2024-01-01T00:00:00Z"
}
```

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

## API 接口列表

### 1. 推荐服务 API

#### 1.1 获取个性化推荐

**接口地址**: `GET /recommend/content`

**请求参数**:
```json
{
  "userId": "string, 必填, 用户ID",
  "size": "integer, 可选, 推荐数量, 默认10",
  "contentType": "string, 可选, 内容类型(article/video/product), 默认mixed"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "recommendations": [
      {
        "contentId": "123456",
        "title": "推荐内容标题",
        "contentType": "article",
        "score": 0.95,
        "reason": "基于您的兴趣推荐"
      }
    ],
    "total": 10
  }
}
```

#### 1.2 记录用户反馈

**接口地址**: `POST /recommend/feedback`

**请求参数**:
```json
{
  "userId": "string, 必填, 用户ID",
  "contentId": "string, 必填, 内容ID",
  "action": "string, 必填, 行为类型(click/like/share/dislike)",
  "timestamp": "long, 可选, 时间戳"
}
```

### 2. 用户服务 API

#### 2.1 用户注册

**接口地址**: `POST /users/register`

**请求参数**:
```json
{
  "username": "string, 必填, 用户名",
  "email": "string, 必填, 邮箱",
  "password": "string, 必填, 密码"
}
```

#### 2.2 用户登录

**接口地址**: `POST /users/login`

**请求参数**:
```json
{
  "username": "string, 必填, 用户名或邮箱",
  "password": "string, 必填, 密码"
}
```

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": "123456",
      "username": "testuser",
      "email": "test@example.com"
    }
  }
}
```

### 3. 内容服务 API

#### 3.1 创建内容

**接口地址**: `POST /contents`

**请求参数**:
```json
{
  "title": "string, 必填, 内容标题",
  "contentType": "string, 必填, 内容类型",
  "contentData": "object, 必填, 内容数据",
  "tags": "array, 可选, 标签列表",
  "categoryId": "integer, 可选, 分类ID"
}
```

#### 3.2 获取内容详情

**接口地址**: `GET /contents/{contentId}`

**响应示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "123456",
    "title": "内容标题",
    "contentType": "article",
    "contentData": {},
    "tags": ["科技", "AI"],
    "publishTime": "2024-01-01T00:00:00Z"
  }
}
```

### 4. 数据收集 API

#### 4.1 记录用户行为

**接口地址**: `POST /behaviors`

**请求参数**:
```json
{
  "userId": "string, 必填, 用户ID",
  "contentId": "string, 必填, 内容ID",
  "actionType": "string, 必填, 行为类型",
  "sessionId": "string, 可选, 会话ID",
  "deviceType": "string, 可选, 设备类型",
  "duration": "integer, 可选, 持续时间(秒)"
}
```

## SDK 使用示例

### Java SDK

```java
// 初始化客户端
RecommendationClient client = new RecommendationClient("your-api-key");

// 获取推荐内容
RecommendRequest request = RecommendRequest.builder()
    .userId("123456")
    .size(10)
    .contentType("article")
    .build();

RecommendResponse response = client.getRecommendations(request);
```

### Python SDK

```python
from recommendation_sdk import RecommendationClient

# 初始化客户端
client = RecommendationClient(api_key="your-api-key")

# 获取推荐内容
response = client.get_recommendations(
    user_id="123456",
    size=10,
    content_type="article"
)
```

## 限流说明

- 每个API Key每分钟最多1000次请求
- 推荐接口每用户每秒最多10次请求
- 超出限制返回429状态码

## 版本更新

### v1.0.0 (2024-01-01)
- 初始版本发布
- 支持基础推荐功能

### v1.1.0 (2024-02-01)
- 新增多样性推荐
- 优化推荐算法性能