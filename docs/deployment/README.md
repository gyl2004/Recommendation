# 智能内容推荐平台部署文档

## 概述

本文档详细介绍了智能内容推荐平台的部署流程，包括本地开发环境、测试环境和生产环境的部署方式。

## 目录结构

```
├── docs/                           # 文档目录
│   ├── api/                       # API文档
│   ├── architecture/              # 系统架构文档
│   └── deployment/                # 部署文档
├── docker/                        # Docker配置
│   ├── Dockerfile.recommendation-service
│   ├── Dockerfile.user-service
│   ├── Dockerfile.content-service
│   ├── Dockerfile.feature-service
│   └── Dockerfile.ranking-service
├── k8s/                          # Kubernetes配置
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secrets.yaml
│   └── recommendation-service.yaml
├── scripts/                      # 部署脚本
│   ├── deploy.sh                 # Linux/Mac部署脚本
│   └── deploy.bat                # Windows部署脚本
├── monitoring/                   # 监控配置
│   └── prometheus/
├── .github/workflows/            # CI/CD配置
│   └── ci-cd.yml
├── docker-compose.prod.yml       # 生产环境Docker Compose
└── README.md
```

## 环境要求

### 基础环境

- **操作系统**: Linux/macOS/Windows
- **Docker**: 20.10+
- **Docker Compose**: 2.0+
- **Kubernetes**: 1.20+ (生产环境)
- **kubectl**: 1.20+
- **Helm**: 3.0+ (可选)

### 开发环境

- **Java**: JDK 11+
- **Maven**: 3.6+
- **Python**: 3.9+
- **Node.js**: 16+ (前端开发)
- **Git**: 2.0+

### 硬件要求

#### 开发环境
- **CPU**: 4核心
- **内存**: 8GB
- **磁盘**: 50GB

#### 测试环境
- **CPU**: 8核心
- **内存**: 16GB
- **磁盘**: 200GB

#### 生产环境
- **CPU**: 16核心+
- **内存**: 32GB+
- **磁盘**: 500GB+

## 快速开始

### 1. 克隆代码

```bash
git clone https://github.com/your-org/recommendation-system.git
cd recommendation-system
```

### 2. 本地开发环境

```bash
# 启动基础设施
docker-compose up -d mysql redis elasticsearch clickhouse rabbitmq

# 构建Java服务
mvn clean package -DskipTests

# 启动推荐服务
cd recommendation-service
mvn spring-boot:run

# 启动Python服务
cd feature-service
pip install -r requirements.txt
uvicorn main:app --reload
```

### 3. Docker Compose部署

```bash
# 生产环境部署
docker-compose -f docker-compose.prod.yml up -d

# 查看服务状态
docker-compose -f docker-compose.prod.yml ps

# 查看日志
docker-compose -f docker-compose.prod.yml logs -f recommendation-service
```

### 4. Kubernetes部署

```bash
# Linux/Mac
./scripts/deploy.sh prod deploy

# Windows
scripts\deploy.bat prod deploy
```

## 详细部署流程

### 1. 环境准备

#### 1.1 安装Docker

**Ubuntu/Debian:**
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
sudo usermod -aG docker $USER
```

**CentOS/RHEL:**
```bash
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io
sudo systemctl start docker
sudo systemctl enable docker
```

**Windows:**
下载并安装 [Docker Desktop](https://www.docker.com/products/docker-desktop)

#### 1.2 安装Kubernetes

**使用minikube (本地测试):**
```bash
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
minikube start --memory=8192 --cpus=4
```

**生产环境建议使用:**
- Amazon EKS
- Google GKE
- Azure AKS
- 自建Kubernetes集群

#### 1.3 安装kubectl

```bash
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
```

### 2. 配置管理

#### 2.1 环境变量配置

创建环境配置文件：

**开发环境 (.env.dev):**
```bash
# 数据库配置
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=recommendation_dev
MYSQL_USERNAME=root
MYSQL_PASSWORD=password

# Redis配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Elasticsearch配置
ELASTICSEARCH_HOST=localhost
ELASTICSEARCH_PORT=9200

# RabbitMQ配置
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=admin
RABBITMQ_PASSWORD=password
```

**生产环境 (.env.prod):**
```bash
# 数据库配置
MYSQL_HOST=mysql.production.com
MYSQL_PORT=3306
MYSQL_DATABASE=recommendation
MYSQL_USERNAME=app_user
MYSQL_PASSWORD=secure_password

# Redis配置
REDIS_HOST=redis.production.com
REDIS_PORT=6379
REDIS_PASSWORD=redis_password

# 其他配置...
```

#### 2.2 Kubernetes Secrets

```bash
# 创建数据库密钥
kubectl create secret generic mysql-secret \
  --from-literal=username=root \
  --from-literal=password=password \
  -n recommendation-system

# 创建Redis密钥
kubectl create secret generic redis-secret \
  --from-literal=password=redis_password \
  -n recommendation-system
```

### 3. 数据库初始化

#### 3.1 MySQL初始化

```sql
-- 创建数据库
CREATE DATABASE recommendation CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户
CREATE USER 'app_user'@'%' IDENTIFIED BY 'secure_password';
GRANT ALL PRIVILEGES ON recommendation.* TO 'app_user'@'%';
FLUSH PRIVILEGES;

-- 执行初始化脚本
SOURCE scripts/sql/init.sql;
```

#### 3.2 ClickHouse初始化

```sql
-- 创建数据库
CREATE DATABASE recommendation;

-- 创建用户行为表
CREATE TABLE recommendation.user_behaviors (
    user_id UInt64,
    content_id UInt64,
    action_type String,
    content_type String,
    session_id String,
    device_type String,
    timestamp DateTime,
    duration UInt32,
    extra_data String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (user_id, timestamp);
```

#### 3.3 Elasticsearch索引创建

```bash
# 创建内容索引
curl -X PUT "elasticsearch:9200/contents" -H 'Content-Type: application/json' -d'
{
  "mappings": {
    "properties": {
      "content_id": {"type": "keyword"},
      "title": {
        "type": "text",
        "analyzer": "ik_max_word",
        "search_analyzer": "ik_smart"
      },
      "content_type": {"type": "keyword"},
      "tags": {"type": "keyword"},
      "category": {"type": "keyword"},
      "embedding": {
        "type": "dense_vector",
        "dims": 128
      },
      "publish_time": {"type": "date"},
      "hot_score": {"type": "float"}
    }
  }
}'
```

### 4. 服务部署

#### 4.1 构建镜像

```bash
# 构建所有服务镜像
./scripts/build-images.sh

# 或者单独构建
docker build -f docker/Dockerfile.recommendation-service -t recommendation-service:latest .
```

#### 4.2 部署到Kubernetes

```bash
# 创建命名空间
kubectl apply -f k8s/namespace.yaml

# 部署配置和密钥
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml

# 部署服务
kubectl apply -f k8s/recommendation-service.yaml

# 检查部署状态
kubectl get pods -n recommendation-system
kubectl get services -n recommendation-system
```

#### 4.3 配置Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: recommendation-ingress
  namespace: recommendation-system
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/rate-limit: "100"
spec:
  rules:
  - host: api.recommendation.com
    http:
      paths:
      - path: /api/v1/recommend
        pathType: Prefix
        backend:
          service:
            name: recommendation-service
            port:
              number: 8080
```

### 5. 监控部署

#### 5.1 Prometheus部署

```bash
# 使用Helm部署
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace \
  --set prometheus.prometheusSpec.retention=30d \
  --set grafana.adminPassword=admin123
```

#### 5.2 配置告警

```bash
# 应用告警规则
kubectl apply -f monitoring/prometheus/rules/
```

### 6. 负载测试

#### 6.1 使用JMeter进行压力测试

```bash
# 安装JMeter
wget https://downloads.apache.org//jmeter/binaries/apache-jmeter-5.4.3.zip
unzip apache-jmeter-5.4.3.zip

# 运行测试
./apache-jmeter-5.4.3/bin/jmeter -n -t performance-tests/load-test.jmx \
  -l results.jtl -e -o report
```

#### 6.2 性能指标验证

- **响应时间**: 95%请求 < 500ms
- **吞吐量**: > 10000 QPS
- **错误率**: < 0.1%
- **资源使用**: CPU < 80%, Memory < 80%

## 运维管理

### 1. 日志管理

#### 1.1 日志收集

```bash
# 查看服务日志
kubectl logs -f deployment/recommendation-service -n recommendation-system

# 使用ELK Stack收集日志
helm install elasticsearch elastic/elasticsearch
helm install kibana elastic/kibana
helm install filebeat elastic/filebeat
```

#### 1.2 日志分析

- **错误日志**: 监控5xx错误和异常堆栈
- **性能日志**: 分析慢查询和响应时间
- **业务日志**: 跟踪推荐效果和用户行为

### 2. 备份策略

#### 2.1 数据库备份

```bash
# MySQL备份
mysqldump -h mysql-host -u username -p recommendation > backup_$(date +%Y%m%d).sql

# 自动备份脚本
#!/bin/bash
BACKUP_DIR="/backup/mysql"
DATE=$(date +%Y%m%d_%H%M%S)
mysqldump -h $MYSQL_HOST -u $MYSQL_USER -p$MYSQL_PASSWORD recommendation | gzip > $BACKUP_DIR/backup_$DATE.sql.gz
```

#### 2.2 配置备份

```bash
# 备份Kubernetes配置
kubectl get all -n recommendation-system -o yaml > k8s-backup-$(date +%Y%m%d).yaml
```

### 3. 扩容策略

#### 3.1 水平扩容

```bash
# 手动扩容
kubectl scale deployment recommendation-service --replicas=5 -n recommendation-system

# 自动扩容 (HPA)
kubectl autoscale deployment recommendation-service --cpu-percent=70 --min=3 --max=10 -n recommendation-system
```

#### 3.2 垂直扩容

```yaml
resources:
  requests:
    memory: "1Gi"
    cpu: "1000m"
  limits:
    memory: "2Gi"
    cpu: "2000m"
```

### 4. 故障处理

#### 4.1 常见问题

**服务启动失败:**
```bash
# 检查Pod状态
kubectl describe pod <pod-name> -n recommendation-system

# 查看日志
kubectl logs <pod-name> -n recommendation-system
```

**数据库连接失败:**
```bash
# 检查数据库连接
kubectl exec -it <pod-name> -n recommendation-system -- nc -zv mysql 3306

# 检查密钥配置
kubectl get secret mysql-secret -n recommendation-system -o yaml
```

**内存不足:**
```bash
# 检查资源使用
kubectl top pods -n recommendation-system

# 调整资源限制
kubectl patch deployment recommendation-service -n recommendation-system -p '{"spec":{"template":{"spec":{"containers":[{"name":"recommendation-service","resources":{"limits":{"memory":"2Gi"}}}]}}}}'
```

#### 4.2 应急预案

1. **服务降级**: 启用缓存降级策略
2. **流量切换**: 使用Ingress切换流量
3. **数据恢复**: 从备份恢复数据
4. **回滚部署**: 回滚到上一个稳定版本

## 安全配置

### 1. 网络安全

```yaml
# NetworkPolicy示例
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: recommendation-network-policy
  namespace: recommendation-system
spec:
  podSelector:
    matchLabels:
      app: recommendation-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: ingress-nginx
    ports:
    - protocol: TCP
      port: 8080
```

### 2. 访问控制

```yaml
# RBAC配置
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: recommendation-system
  name: recommendation-role
rules:
- apiGroups: [""]
  resources: ["pods", "services"]
  verbs: ["get", "list", "watch"]
```

### 3. 数据加密

- **传输加密**: 使用TLS/HTTPS
- **存储加密**: 数据库和文件系统加密
- **密钥管理**: 使用Kubernetes Secrets或外部密钥管理系统

## 性能优化

### 1. 应用层优化

- **连接池**: 优化数据库连接池配置
- **缓存策略**: 多级缓存和缓存预热
- **异步处理**: 使用消息队列异步处理

### 2. 基础设施优化

- **资源配置**: 合理配置CPU和内存
- **存储优化**: 使用SSD和合适的存储类
- **网络优化**: 优化网络配置和带宽

### 3. 数据库优化

- **索引优化**: 创建合适的数据库索引
- **查询优化**: 优化SQL查询语句
- **分库分表**: 大数据量时进行分库分表

## 故障排查

### 1. 常用命令

```bash
# 查看Pod状态
kubectl get pods -n recommendation-system

# 查看Pod详细信息
kubectl describe pod <pod-name> -n recommendation-system

# 查看服务日志
kubectl logs -f <pod-name> -n recommendation-system

# 进入Pod调试
kubectl exec -it <pod-name> -n recommendation-system -- /bin/bash

# 查看资源使用
kubectl top pods -n recommendation-system
kubectl top nodes
```

### 2. 监控指标

- **系统指标**: CPU、内存、磁盘、网络
- **应用指标**: QPS、响应时间、错误率
- **业务指标**: 推荐点击率、转化率

### 3. 日志分析

```bash
# 查看错误日志
kubectl logs <pod-name> -n recommendation-system | grep ERROR

# 实时监控日志
kubectl logs -f <pod-name> -n recommendation-system | grep -E "(ERROR|WARN)"
```

## 联系方式

如有问题，请联系：

- **开发团队**: dev-team@company.com
- **运维团队**: ops-team@company.com
- **项目经理**: pm@company.com

## 更新日志

### v1.0.0 (2024-01-01)
- 初始版本发布
- 支持Docker和Kubernetes部署

### v1.1.0 (2024-02-01)
- 新增监控和告警配置
- 优化部署脚本
- 增加故障排查文档