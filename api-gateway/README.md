# API网关配置文档

## 概述

本API网关基于Nginx和Kong构建，为智能内容推荐平台提供统一的API入口，实现请求路由、负载均衡、限流、认证授权等功能。

## 架构组件

### 1. Nginx (负载均衡器)
- **端口**: 80 (HTTP), 443 (HTTPS)
- **功能**: 
  - 请求路由和负载均衡
  - SSL终端处理
  - 静态文件服务
  - 基础限流和安全防护

### 2. Kong (API网关)
- **端口**: 8000 (Proxy), 8001 (Admin), 8002 (Manager)
- **功能**:
  - 高级API管理
  - 插件生态系统
  - 认证授权
  - 监控和分析

### 3. Redis (缓存和限流存储)
- **端口**: 6379
- **功能**:
  - 限流计数器存储
  - 会话缓存
  - 插件数据存储

### 4. Prometheus + Grafana (监控)
- **端口**: 9090 (Prometheus), 3000 (Grafana)
- **功能**:
  - 指标收集和存储
  - 可视化监控面板
  - 告警管理

## 快速开始

### 1. 部署API网关

```bash
# 克隆项目并进入API网关目录
cd api-gateway

# 给脚本执行权限
chmod +x scripts/*.sh

# 部署API网关
./scripts/deploy.sh
```

### 2. 验证部署

```bash
# 检查服务状态
./scripts/manage.sh health

# 查看服务列表
./scripts/manage.sh services

# 查看路由列表
./scripts/manage.sh routes
```

### 3. 测试API访问

```bash
# 测试健康检查
curl http://localhost/health

# 测试推荐API（需要JWT token）
curl -H "Authorization: Bearer <your-jwt-token>" \
     http://localhost/api/v1/recommend?userId=123

# 测试用户API
curl -H "Authorization: Bearer <your-jwt-token>" \
     http://localhost/api/v1/users/123
```

## 配置说明

### Nginx配置

#### 主要配置文件
- `nginx/nginx.conf` - 主配置文件
- `nginx/conf.d/api-routes.conf` - API路由配置

#### 关键配置项

```nginx
# 限流配置
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=100r/s;
limit_req_zone $binary_remote_addr zone=login_limit:10m rate=5r/s;

# 上游服务器配置
upstream recommendation_service {
    least_conn;
    server recommendation-service:8080 max_fails=3 fail_timeout=30s;
    server recommendation-service-2:8080 max_fails=3 fail_timeout=30s backup;
    keepalive 32;
}
```

### Kong配置

#### 声明式配置文件
- `kong/kong.yml` - Kong声明式配置

#### 主要配置项

```yaml
# 服务定义
services:
  - name: recommendation-service
    url: http://recommendation-service:8080
    connect_timeout: 5000
    write_timeout: 10000
    read_timeout: 10000

# 路由定义
routes:
  - name: recommendation-routes
    service: recommendation-service
    paths:
      - /api/v1/recommend

# 插件配置
plugins:
  - name: rate-limiting
    config:
      minute: 1000
      hour: 10000
      policy: redis
```

## 安全配置

### 1. JWT认证

API网关使用JWT进行用户认证，支持以下方式传递token：

- **请求头**: `Authorization: Bearer <token>`
- **URL参数**: `?jwt=<token>`
- **Cookie**: `jwt=<token>`

#### JWT Token格式

```json
{
  "sub": "user123",
  "username": "john_doe",
  "role": "user",
  "permissions": ["read", "write"],
  "exp": 1640995200,
  "iat": 1640908800
}
```

### 2. 限流策略

#### 全局限流
- **每分钟**: 1000 请求
- **每小时**: 10000 请求
- **每天**: 100000 请求

#### 服务特定限流
- **推荐服务**: 500/分钟
- **登录接口**: 10/分钟
- **内容服务**: 800/分钟

### 3. 安全头设置

```nginx
add_header X-Frame-Options DENY;
add_header X-Content-Type-Options nosniff;
add_header X-XSS-Protection "1; mode=block";
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains";
```

## 监控和告警

### 1. 监控指标

#### 核心指标
- **响应时间**: 95%分位数 < 500ms
- **错误率**: < 1%
- **QPS**: 支持10000+
- **可用性**: > 99.9%

#### 业务指标
- **推荐请求量**: 按时间段统计
- **用户活跃度**: 按用户类型统计
- **API使用情况**: 按接口统计

### 2. 告警规则

#### 关键告警
- 响应时间超过500ms
- 错误率超过5%
- 服务不可用
- SSL证书即将过期

#### 告警通知
- 邮件通知
- 钉钉/企业微信通知
- 短信通知（紧急情况）

### 3. 监控面板

访问 `http://localhost:3000` 查看Grafana监控面板：

- **API网关概览**: 整体性能指标
- **服务详情**: 各服务详细监控
- **错误分析**: 错误统计和分析
- **用户行为**: 用户访问模式分析

## 运维管理

### 1. 日常运维命令

```bash
# 查看服务状态
./scripts/manage.sh status

# 查看实时日志
./scripts/manage.sh logs nginx
./scripts/manage.sh logs kong

# 重新加载配置
./scripts/manage.sh reload-nginx
./scripts/manage.sh reload-kong

# 性能测试
./scripts/manage.sh test http://localhost/health 50 1000
```

### 2. 配置管理

```bash
# 添加新服务
./scripts/manage.sh add-service new-service http://new-service:8080

# 添加新路由
./scripts/manage.sh add-route new-service /api/v1/new

# 启用插件
./scripts/manage.sh enable-plugin rate-limiting new-service '{"minute":100}'

# 备份配置
./scripts/manage.sh backup
```

### 3. 故障排查

#### 常见问题

1. **服务无响应**
   ```bash
   # 检查服务状态
   docker-compose ps
   
   # 查看服务日志
   docker-compose logs service-name
   
   # 重启服务
   docker-compose restart service-name
   ```

2. **配置错误**
   ```bash
   # 验证Nginx配置
   docker-compose exec nginx nginx -t
   
   # 验证Kong配置
   docker-compose exec kong kong config parse /kong/declarative/kong.yml
   ```

3. **性能问题**
   ```bash
   # 查看系统资源
   docker stats
   
   # 查看网络连接
   netstat -tulpn
   
   # 分析访问日志
   tail -f logs/nginx/access.log | grep "slow"
   ```

## 扩展和优化

### 1. 水平扩展

```yaml
# docker-compose.yml 扩展示例
nginx:
  deploy:
    replicas: 2
    
kong:
  deploy:
    replicas: 3
```

### 2. 性能优化

#### Nginx优化
```nginx
worker_processes auto;
worker_connections 2048;
keepalive_timeout 65;
client_max_body_size 10M;
```

#### Kong优化
```yaml
environment:
  KONG_NGINX_WORKER_PROCESSES: auto
  KONG_NGINX_WORKER_CONNECTIONS: 2048
  KONG_MEM_CACHE_SIZE: 256m
```

### 3. 缓存策略

```nginx
# 静态资源缓存
location /static/ {
    expires 1y;
    add_header Cache-Control "public, immutable";
}

# API响应缓存
proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=api_cache:10m;
proxy_cache api_cache;
proxy_cache_valid 200 5m;
```

## 安全最佳实践

### 1. 网络安全
- 使用HTTPS加密传输
- 配置防火墙规则
- 限制管理接口访问

### 2. 认证授权
- 强制JWT认证
- 实现细粒度权限控制
- 定期轮换密钥

### 3. 数据保护
- 敏感数据脱敏
- 访问日志审计
- 定期安全扫描

## 联系方式

如有问题或建议，请联系：
- 邮箱: devops@recommendation.com
- 钉钉群: API网关运维群
- 文档: https://docs.recommendation.com/api-gateway