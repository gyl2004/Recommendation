# 🛒 智能电商推荐平台

一个基于微服务架构的完整电商推荐系统，提供个性化商品推荐、购物车管理、用户偏好分析等功能。

## 🌟 项目特色

- **智能推荐算法**：基于用户行为和偏好的个性化推荐
- **微服务架构**：推荐服务、商品服务、用户服务独立部署
- **现代化前端**：响应式设计，流畅的用户体验
- **完整购物流程**：从商品浏览到购物车结算的完整体验
- **实时数据交互**：前后端实时通信，动态更新

## 🏗️ 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   推荐服务       │    │   商品服务       │    │   用户服务       │
│   (8080)        │    │   (8082)        │    │   (8081)        │
│                 │    │                 │    │                 │
│ • 个性化推荐     │    │ • 商品管理       │    │ • 用户信息       │
│ • 相似商品       │    │ • 购物车        │    │ • 用户偏好       │
│ • 推荐算法       │    │ • 商品搜索       │    │ • 订单历史       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │   前端界面       │
                    │   (3000)        │
                    │                 │
                    │ • 商品展示       │
                    │ • 购物车        │
                    │ • 搜索过滤       │
                    │ • 用户交互       │
                    └─────────────────┘
```

## 🚀 快速开始

### 环境要求

- **Java**: 11+
- **Maven**: 3.6+
- **Python**: 3.8+ (用于启动Web服务器)
- **操作系统**: Linux/macOS/Windows

### 安装步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd intelligent-content-recommendation
```

2. **启动基础设施服务**
```bash
docker-compose up -d mysql redis elasticsearch rabbitmq clickhouse
```

3. **构建并启动微服务**
```bash
# 推荐服务
cd simple-services/recommendation-service
mvn clean package
nohup java -jar target/simple-recommendation-service-1.0.0.jar > ../../logs/recommendation.log 2>&1 &

# 商品服务
cd ../content-service
mvn clean package
nohup java -jar target/simple-content-service-1.0.0.jar > ../../logs/products.log 2>&1 &

# 用户服务
cd ../user-service
mvn clean package
nohup java -jar target/simple-user-service-1.0.0.jar > ../../logs/users.log 2>&1 &
```

4. **启动前端服务**
```bash
cd web
python3 -m http.server 3000
```

5. **访问系统**
```
http://localhost:3000/ecommerce.html
```

## 🎯 核心功能

### 🔍 智能推荐
- **个性化推荐**：基于用户历史行为和偏好
- **相似商品推荐**：基于商品特征的相似度计算
- **实时推荐评分**：动态调整推荐权重

### 🛍️ 电商功能
- **商品浏览**：支持分类、品牌、价格筛选
- **搜索功能**：实时搜索，智能建议
- **购物车管理**：添加、删除、数量调整
- **商品收藏**：个人收藏夹管理

### 👤 用户体验
- **响应式设计**：适配各种设备屏幕
- **流畅交互**：现代化UI设计
- **实时通知**：操作反馈和状态提示

## 📊 API 文档

### 推荐服务 API

#### 获取个性化推荐
```http
GET /api/v1/recommend/products?userId={userId}&size={size}&category={category}
```

**响应示例：**
```json
{
  "success": true,
  "userId": "123",
  "size": 10,
  "recommendations": [
    {
      "productId": 10001,
      "name": "iPhone 15 Pro Max 256GB",
      "price": 9999.0,
      "rating": 4.8,
      "recommendScore": 0.95,
      "category": "手机",
      "brand": "Apple"
    }
  ]
}
```

#### 获取相似商品
```http
GET /api/v1/recommend/similar/{productId}
```

### 商品服务 API

#### 获取商品列表
```http
GET /api/v1/products?page={page}&size={size}&category={category}&brand={brand}
```

#### 搜索商品
```http
GET /api/v1/products/search?query={query}&page={page}&size={size}
```

#### 购物车操作
```http
POST /api/v1/cart/add
GET /api/v1/cart/{userId}
```

### 用户服务 API

#### 获取用户信息
```http
GET /api/v1/users/{userId}
```

#### 获取用户偏好
```http
GET /api/v1/users/{userId}/preferences
```

#### 获取用户订单
```http
GET /api/v1/users/{userId}/orders
```

## 🛠️ 技术栈

### 后端技术
- **Spring Boot 2.7.14**: 微服务框架
- **Java 11**: 编程语言
- **Maven**: 项目管理和构建
- **Spring Web**: RESTful API
- **CORS配置**: 跨域资源共享

### 前端技术
- **HTML5/CSS3**: 页面结构和样式
- **JavaScript ES6+**: 交互逻辑
- **Fetch API**: HTTP请求
- **响应式设计**: 移动端适配

### 基础设施
- **MySQL**: 主数据库
- **Redis**: 缓存系统
- **Elasticsearch**: 搜索引擎
- **RabbitMQ**: 消息队列
- **ClickHouse**: 分析数据库
- **Docker**: 容器化部署

## 📁 项目结构

```
intelligent-content-recommendation/
├── simple-services/                 # 微服务目录
│   ├── recommendation-service/      # 推荐服务
│   │   ├── src/main/java/
│   │   │   └── com/recommendation/
│   │   │       ├── RecommendationApplication.java
│   │   │       └── CorsConfig.java
│   │   └── pom.xml
│   ├── content-service/            # 商品服务
│   │   ├── src/main/java/
│   │   │   └── com/content/
│   │   │       ├── ContentApplication.java
│   │   │       └── CorsConfig.java
│   │   └── pom.xml
│   └── user-service/               # 用户服务
│       ├── src/main/java/
│       │   └── com/user/
│       │       ├── UserApplication.java
│       │       └── CorsConfig.java
│       └── pom.xml
├── web/                            # 前端文件
│   └── ecommerce.html             # 电商平台主页面
├── docker-compose.yml             # Docker编排文件
├── logs/                          # 日志目录
└── README_ECOMMERCE.md           # 项目文档
```

## 🔧 配置说明

### 服务端口配置
- **推荐服务**: 8080
- **商品服务**: 8082  
- **用户服务**: 8081
- **前端服务**: 3000

### CORS配置
所有服务都配置了CORS支持，允许跨域访问：
```java
@CrossOrigin(origins = "*", allowedHeaders = "*")
```

### 数据库配置
```yaml
# MySQL配置
MYSQL_ROOT_PASSWORD: root123
MYSQL_DATABASE: recommendation_platform
MYSQL_USER: recommendation_user
MYSQL_PASSWORD: recommendation_pass
```

## 🧪 测试指南

### 功能测试
1. **推荐功能测试**
```bash
curl "http://localhost:8080/api/v1/recommend/products?userId=123&size=5"
```

2. **商品搜索测试**
```bash
curl "http://localhost:8082/api/v1/products/search?query=iPhone"
```

3. **用户信息测试**
```bash
curl "http://localhost:8081/api/v1/users/123"
```

### 前端测试
- 访问 `http://localhost:3000/ecommerce.html`
- 测试商品浏览、搜索、购物车等功能
- 验证响应式设计在不同设备上的表现

## 🚀 部署指南

### 开发环境部署
1. 按照"快速开始"步骤启动所有服务
2. 确保所有端口未被占用
3. 检查服务健康状态

### 生产环境部署
1. 使用Docker Compose进行容器化部署
2. 配置负载均衡和服务发现
3. 设置监控和日志收集
4. 配置HTTPS和安全策略

## 📈 性能优化

### 推荐算法优化
- 实现缓存机制减少计算开销
- 使用异步处理提高响应速度
- 批量处理推荐请求

### 数据库优化
- 添加适当的索引
- 使用连接池管理数据库连接
- 实现读写分离

### 前端优化
- 图片懒加载
- 组件化开发
- 缓存静态资源

## 🔒 安全考虑

- **输入验证**: 所有API输入都进行验证
- **CORS配置**: 合理配置跨域访问权限
- **数据加密**: 敏感数据传输加密
- **访问控制**: 实现用户认证和授权

## 🤝 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 📞 联系方式

- 项目维护者: [Your Name]
- 邮箱: [your.email@example.com]
- 项目链接: [https://github.com/yourusername/intelligent-content-recommendation](https://github.com/yourusername/intelligent-content-recommendation)

## 🙏 致谢

感谢所有为这个项目做出贡献的开发者和用户！

---

## 🎯 快速体验

想要快速体验这个电商推荐平台？

1. 确保Java 11+和Python 3.8+已安装
2. 运行快速启动脚本
3. 在浏览器中访问 `http://localhost:3000/ecommerce.html`
4. 开始你的智能购物之旅！

**立即开始体验智能电商推荐的魅力！** 🚀