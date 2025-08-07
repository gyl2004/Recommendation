# 用户服务 (User Service)

智能内容推荐平台的用户服务模块，提供用户管理、行为数据收集和用户画像构建功能。

## 功能特性

### 1. 用户基础信息管理
- 用户注册、登录、信息更新
- 用户信息的CRUD操作和数据验证
- 用户状态管理（启用/禁用）
- 支持用户名、邮箱、手机号的唯一性验证

### 2. 用户行为数据收集
- 实时收集用户浏览、点击、点赞、分享、评论等行为数据
- 支持批量行为数据收集
- 异步消息队列处理（RabbitMQ）
- 行为数据统计和分析

### 3. 用户画像构建算法
- 基于用户行为的兴趣标签计算
- 内容类型偏好度计算和权重更新
- 用户画像数据的实时更新和缓存机制（Redis）
- 活跃度分数和画像质量评估

## 技术栈

- **框架**: Spring Boot 2.7.14
- **数据库**: MySQL 8.0 + JPA/Hibernate
- **缓存**: Redis
- **消息队列**: RabbitMQ
- **序列化**: Jackson
- **验证**: Spring Boot Validation
- **测试**: JUnit 5 + Mockito

## API接口

### 用户管理接口
- `POST /api/v1/users/register` - 用户注册
- `POST /api/v1/users/login` - 用户登录
- `PUT /api/v1/users/{userId}` - 更新用户信息
- `GET /api/v1/users/{userId}` - 获取用户信息
- `GET /api/v1/users/active` - 获取所有激活用户

### 用户行为接口
- `POST /api/v1/behaviors/collect` - 收集单个用户行为
- `POST /api/v1/behaviors/collect/batch` - 批量收集用户行为
- `GET /api/v1/behaviors/user/{userId}/history` - 获取用户行为历史
- `GET /api/v1/behaviors/content/{contentId}` - 获取内容行为统计

### 用户画像接口
- `POST /api/v1/profiles/build/{userId}` - 构建用户画像
- `PUT /api/v1/profiles/{userId}` - 更新用户画像
- `GET /api/v1/profiles/{userId}` - 获取用户画像
- `POST /api/v1/profiles/rebuild/{userId}` - 重新构建用户画像

## 数据模型

### 用户实体 (UserEntity)
- 基础信息：用户名、邮箱、手机号
- 画像数据：JSON格式存储用户画像信息
- 状态管理：用户激活状态

### 用户行为实体 (UserBehaviorEntity)
- 行为信息：用户ID、内容ID、行为类型
- 上下文信息：会话ID、设备类型、时间戳
- 扩展数据：JSON格式存储额外信息

## 消息队列

### 用户行为队列
- **队列名**: `user.behavior.queue`
- **交换机**: `user.behavior.exchange`
- **路由键**: `user.behavior.routing.key`
- **用途**: 异步处理用户行为数据，触发画像更新

### 用户画像队列
- **队列名**: `user.profile.queue`
- **交换机**: `user.profile.exchange`
- **路由键**: `user.profile.routing.key`
- **用途**: 通知其他服务用户画像更新

## 缓存策略

### Redis缓存
- **用户画像**: `user:profile:{userId}` (24小时过期)
- **兴趣标签**: `user:interests:{userId}` (24小时过期)
- **内容偏好**: `user:preferences:{userId}` (24小时过期)

## 配置说明

### 数据库配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/recommendation_db
    username: root
    password: password
```

### Redis配置
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
```

### RabbitMQ配置
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

## 运行和测试

### 启动服务
```bash
mvn spring-boot:run
```

### 运行测试
```bash
mvn test
```

### 构建项目
```bash
mvn clean package
```

## 监控和日志

- 服务健康检查：`/actuator/health`
- 应用信息：`/actuator/info`
- 指标监控：`/actuator/metrics`
- Prometheus指标：`/actuator/prometheus`

## 注意事项

1. 确保MySQL、Redis、RabbitMQ服务正常运行
2. 数据库表结构需要预先创建
3. 用户画像构建是计算密集型操作，建议异步处理
4. 缓存失效策略需要根据业务需求调整
5. 消息队列的重试机制和死信队列需要配置

## 后续扩展

1. 支持更多用户行为类型
2. 增加机器学习算法优化画像构建
3. 实现用户聚类和相似用户推荐
4. 添加用户隐私保护机制
5. 支持多租户架构