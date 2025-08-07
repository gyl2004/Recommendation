# 智能内容推荐平台启动指南

## 🚀 快速启动

### 方式一：Docker Compose 一键启动（推荐）

**开发环境启动：**
```bash
# 1. 克隆项目
git clone <repository-url>
cd intelligent-content-recommendation

# 2. 启动所有服务（包括基础设施）
docker-compose up -d

# 3. 等待服务启动完成（约2-3分钟）
docker-compose ps

# 4. 验证服务状态
curl http://localhost:8080/actuator/health
```

**生产环境启动：**
```bash
# 启动生产环境
docker-compose -f docker-compose.prod.yml up -d
```

### 方式二：分步启动

#### 1. 启动基础设施服务
```bash
# 启动数据库和中间件
docker-compose up -d mysql redis elasticsearch rabbitmq clickhouse

# 等待基础设施就绪
sleep 30
```

#### 2. 启动应用服务
```bash
# 启动推荐服务
docker-compose up -d recommendation-service

# 启动用户服务
docker-compose up -d user-service

# 启动内容服务
docker-compose up -d content-service

# 启动特征服务
docker-compose up -d feature-service
```

#### 3. 启动监控服务
```bash
# 启动监控组件
./start-monitoring.sh
```

## 📋 系统要求

### 硬件要求
- **CPU**: 最少4核，推荐8核+
- **内存**: 最少8GB，推荐16GB+
- **磁盘**: 最少50GB可用空间
- **网络**: 稳定的网络连接

### 软件要求
- **Docker**: 20.10+
- **Docker Compose**: 2.0+
- **Git**: 2.0+
- **JDK**: 11+ (如果本地开发)
- **Maven**: 3.6+ (如果本地开发)
- **Python**: 3.8+ (如果本地开发)

## 🔧 环境配置

### 1. 环境变量配置

创建 `.env` 文件：
```bash
# 数据库配置
MYSQL_ROOT_PASSWORD=root123
MYSQL_DATABASE=recommendation_platform
MYSQL_USER=recommendation_user
MYSQL_PASSWORD=recommendation_pass

# Redis配置
REDIS_PASSWORD=redis123

# 应用配置
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080

# 监控配置
GRAFANA_ADMIN_PASSWORD=admin123
```

### 2. 数据库初始化

```bash
# 等待MySQL启动
docker-compose exec mysql mysql -uroot -proot123 -e "SELECT 1"

# 执行初始化脚本
docker-compose exec mysql mysql -uroot -proot123 recommendation_platform < /docker-entrypoint-initdb.d/init.sql
```

### 3. 缓存预热

```bash
# Redis缓存预热
docker-compose exec redis redis-cli ping

# 预热推荐数据
curl -X POST http://localhost:8080/api/v1/admin/cache/warmup
```

## 🌐 服务访问地址

### 应用服务
| 服务 | 地址 | 描述 |
|------|------|------|
| 推荐服务 | http://localhost:8080 | 主要推荐API |
| 用户服务 | http://localhost:8081 | 用户管理API |
| 内容服务 | http://localhost:8082 | 内容管理API |
| 特征服务 | http://localhost:8003 | 特征提取API |

### 基础设施服务
| 服务 | 地址 | 用户名/密码 |
|------|------|------------|
| MySQL | localhost:3306 | root/root123 |
| Redis | localhost:6379 | - |
| Elasticsearch | http://localhost:9200 | - |
| RabbitMQ | http://localhost:15672 | admin/admin123 |
| ClickHouse | http://localhost:8123 | default/- |

### 监控服务
| 服务 | 地址 | 用户名/密码 |
|------|------|------------|
| Grafana | http://localhost:3000 | admin/admin123 |
| Prometheus | http://localhost:9090 | - |
| AlertManager | http://localhost:9093 | - |

## 🔍 健康检查

### 自动健康检查
```bash
# 检查所有服务状态
docker-compose ps

# 检查应用健康状态
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

### 手动验证
```bash
# 测试推荐API
curl "http://localhost:8080/api/v1/recommend/content?userId=1&size=10"

# 测试用户API
curl "http://localhost:8081/api/v1/users/1"

# 测试内容API
curl "http://localhost:8082/api/v1/contents?page=0&size=10"
```

## 📊 监控和日志

### 查看日志
```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f recommendation-service

# 查看最近100行日志
docker-compose logs --tail=100 recommendation-service
```

### 监控面板
1. 访问 Grafana: http://localhost:3000
2. 使用 admin/admin123 登录
3. 查看预配置的仪表板：
   - 系统概览
   - 应用性能
   - 数据库监控
   - 缓存监控

## 🚨 故障排除

### 常见问题

#### 1. 服务启动失败
```bash
# 检查端口占用
netstat -tulpn | grep :8080

# 检查Docker资源
docker system df
docker system prune -f

# 重新启动服务
docker-compose down
docker-compose up -d
```

#### 2. 数据库连接失败
```bash
# 检查MySQL状态
docker-compose exec mysql mysqladmin -uroot -proot123 ping

# 检查网络连接
docker-compose exec recommendation-service ping mysql

# 重置数据库
docker-compose down mysql
docker volume rm $(docker volume ls -q | grep mysql)
docker-compose up -d mysql
```

#### 3. 内存不足
```bash
# 检查内存使用
docker stats

# 清理未使用的镜像和容器
docker system prune -a -f

# 调整JVM内存设置
export JAVA_OPTS="-Xms512m -Xmx1g"
```

#### 4. 性能问题
```bash
# 检查系统资源
htop
iostat -x 1

# 查看应用性能指标
curl http://localhost:8080/actuator/metrics

# 执行性能测试
cd integration-tests/performance
./run_complete_performance_test.sh
```

## 🔄 开发模式

### 本地开发启动
```bash
# 1. 启动基础设施
docker-compose up -d mysql redis elasticsearch rabbitmq

# 2. 本地启动应用（IDE或命令行）
cd recommendation-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 3. 启动Python服务
cd feature-service
pip install -r requirements.txt
python app.py
```

### 热重载开发
```bash
# 使用Spring Boot DevTools
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.devtools.restart.enabled=true"

# 使用Docker开发模式
docker-compose -f docker-compose.dev.yml up -d
```

## 📦 部署到生产环境

### Kubernetes部署
```bash
# 使用部署脚本
./scripts/deploy.sh prod deploy

# 手动部署
kubectl apply -f k8s/
```

### Docker Swarm部署
```bash
# 初始化Swarm
docker swarm init

# 部署Stack
docker stack deploy -c docker-compose.prod.yml recommendation-system
```

## 🧪 性能测试

### 执行性能测试
```bash
# 完整性能测试
cd integration-tests/performance
./run_complete_performance_test.sh

# 自定义测试参数
BASE_URL=http://localhost:8080 \
USERS=1000 \
DURATION=300 \
./run_performance_test.sh
```

### 性能目标
- **QPS**: 10,000+
- **响应时间**: P95 < 500ms
- **可用性**: > 99.9%
- **并发用户**: 1000+

## 📚 API文档

### Swagger UI
- 推荐服务: http://localhost:8080/swagger-ui.html
- 用户服务: http://localhost:8081/swagger-ui.html
- 内容服务: http://localhost:8082/swagger-ui.html

### API示例
```bash
# 获取个性化推荐
curl -X GET "http://localhost:8080/api/v1/recommend/content?userId=123&size=20&contentType=mixed"

# 提交用户反馈
curl -X POST "http://localhost:8080/api/v1/recommend/feedback" \
  -H "Content-Type: application/json" \
  -d '{"userId":123,"contentId":456,"actionType":"click"}'

# 获取用户信息
curl -X GET "http://localhost:8081/api/v1/users/123"

# 搜索内容
curl -X GET "http://localhost:8082/api/v1/contents/search?query=技术&page=0&size=10"
```

## 🔐 安全配置

### 生产环境安全
1. **修改默认密码**
2. **启用HTTPS**
3. **配置防火墙**
4. **启用访问日志**
5. **定期安全更新**

### 环境隔离
- 开发环境：`docker-compose.yml`
- 测试环境：`docker-compose.staging.yml`
- 生产环境：`docker-compose.prod.yml`

## 📞 技术支持

### 获取帮助
1. 查看日志文件
2. 检查健康检查端点
3. 查看监控面板
4. 参考故障排除指南

### 联系方式
- 技术文档：查看项目README
- 问题反馈：提交GitHub Issue
- 紧急支持：联系运维团队

---

## 🎯 快速验证清单

启动完成后，请验证以下项目：

- [ ] 所有Docker容器正常运行
- [ ] 数据库连接正常
- [ ] Redis缓存可访问
- [ ] Elasticsearch搜索正常
- [ ] 推荐API返回结果
- [ ] 监控面板显示数据
- [ ] 日志输出正常
- [ ] 性能测试通过

完成以上检查后，您的智能内容推荐平台就可以正常使用了！