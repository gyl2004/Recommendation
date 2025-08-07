# 智能内容推荐平台

## 项目概述

智能内容推荐平台是一个类似今日头条/抖音的推荐引擎系统，支持文章、视频、商品等多种内容类型的个性化推荐。系统通过收集用户行为数据，运用机器学习算法，为用户提供精准的内容推荐服务。

## 项目架构

### 模块结构

```
intelligent-content-recommendation/
├── recommendation-common/          # 公共模块
│   ├── domain/                    # 领域模型
│   ├── dto/                       # 数据传输对象
│   ├── service/                   # 服务接口
│   └── exception/                 # 异常定义
├── recommendation-service/         # 推荐服务
├── user-service/                  # 用户服务
├── content-service/               # 内容服务
└── data-collection-service/       # 数据收集服务
```

### 技术栈

- **后端框架**: Spring Boot 2.7.14
- **数据库**: MySQL 8.0
- **缓存**: Redis
- **搜索引擎**: Elasticsearch 7.17
- **消息队列**: RabbitMQ
- **构建工具**: Maven 3.6+
- **Java版本**: JDK 11

## 核心功能

### 1. 用户行为数据收集
- 浏览行为记录
- 点击行为追踪
- 交互行为分析
- 异步数据处理

### 2. 多类型内容管理
- 文章内容管理
- 视频内容管理
- 商品内容管理
- 内容特征提取

### 3. 实时推荐服务
- 个性化推荐
- 热门内容推荐
- 多样性保证
- 降级策略

### 4. 用户画像构建
- 兴趣标签计算
- 行为偏好分析
- 实时特征更新
- 画像数据缓存

## 快速开始

### 环境要求

- JDK 11+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- Elasticsearch 7.17+
- RabbitMQ 3.8+

### 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd intelligent-content-recommendation

# 编译项目
mvn clean compile

# 运行测试
mvn test

# 打包项目
mvn clean package
```

### 启动服务

```bash
# 启动推荐服务
cd recommendation-service
mvn spring-boot:run

# 启动用户服务
cd user-service
mvn spring-boot:run

# 启动内容服务
cd content-service
mvn spring-boot:run

# 启动数据收集服务
cd data-collection-service
mvn spring-boot:run
```

### 服务端口

- 推荐服务: 8080
- 用户服务: 8081
- 内容服务: 8082
- 数据收集服务: 8083

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

### Elasticsearch配置

```yaml
elasticsearch:
  host: localhost
  port: 9200
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

## API文档

### 推荐服务API

- `GET /api/v1/recommend/content` - 获取推荐内容
- `POST /api/v1/recommend/feedback` - 记录推荐反馈

### 用户服务API

- `GET /api/v1/users/{id}` - 获取用户信息
- `POST /api/v1/users` - 创建用户
- `PUT /api/v1/users/{id}` - 更新用户信息

### 内容服务API

- `GET /api/v1/contents/{id}` - 获取内容详情
- `POST /api/v1/contents` - 创建内容
- `GET /api/v1/contents/search` - 搜索内容

### 数据收集服务API

- `POST /api/v1/behaviors` - 记录用户行为

## 开发指南

### 代码规范

- 使用Lombok减少样板代码
- 统一异常处理
- API响应格式标准化
- 完善的参数校验

### 测试策略

- 单元测试覆盖率 > 80%
- 集成测试验证核心流程
- 性能测试确保响应时间

### 监控指标

- 推荐服务响应时间
- 缓存命中率
- 数据库连接池状态
- 消息队列积压情况

## 部署说明

### Docker部署

```bash
# 构建镜像
docker build -t recommendation-service .

# 运行容器
docker run -p 8080:8080 recommendation-service
```

### 生产环境配置

- 数据库连接池优化
- Redis集群配置
- Elasticsearch集群配置
- 负载均衡配置

## 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交代码变更
4. 创建Pull Request

## 许可证

本项目采用MIT许可证，详见LICENSE文件。