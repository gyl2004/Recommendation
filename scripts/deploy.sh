#!/bin/bash

# 智能内容推荐平台部署脚本
# 使用方法: ./deploy.sh [environment] [action]
# 环境: dev, staging, prod
# 操作: deploy, rollback, status

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置变量
ENVIRONMENT=${1:-dev}
ACTION=${2:-deploy}
PROJECT_NAME="recommendation-system"
DOCKER_REGISTRY="ghcr.io/your-org"
KUBECTL_TIMEOUT="300s"

# 日志函数
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查依赖
check_dependencies() {
    log_info "检查部署依赖..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker 未安装"
        exit 1
    fi
    
    if ! command -v kubectl &> /dev/null; then
        log_error "kubectl 未安装"
        exit 1
    fi
    
    if ! command -v helm &> /dev/null; then
        log_warning "Helm 未安装，将跳过 Helm 相关操作"
    fi
    
    log_success "依赖检查完成"
}

# 设置环境变量
setup_environment() {
    log_info "设置 ${ENVIRONMENT} 环境变量..."
    
    case $ENVIRONMENT in
        dev)
            NAMESPACE="recommendation-dev"
            REPLICAS=1
            RESOURCES_REQUESTS_CPU="100m"
            RESOURCES_REQUESTS_MEMORY="256Mi"
            RESOURCES_LIMITS_CPU="500m"
            RESOURCES_LIMITS_MEMORY="512Mi"
            ;;
        staging)
            NAMESPACE="recommendation-staging"
            REPLICAS=2
            RESOURCES_REQUESTS_CPU="200m"
            RESOURCES_REQUESTS_MEMORY="512Mi"
            RESOURCES_LIMITS_CPU="1000m"
            RESOURCES_LIMITS_MEMORY="1Gi"
            ;;
        prod)
            NAMESPACE="recommendation-system"
            REPLICAS=3
            RESOURCES_REQUESTS_CPU="500m"
            RESOURCES_REQUESTS_MEMORY="512Mi"
            RESOURCES_LIMITS_CPU="2000m"
            RESOURCES_LIMITS_MEMORY="2Gi"
            ;;
        *)
            log_error "不支持的环境: $ENVIRONMENT"
            exit 1
            ;;
    esac
    
    export NAMESPACE REPLICAS RESOURCES_REQUESTS_CPU RESOURCES_REQUESTS_MEMORY RESOURCES_LIMITS_CPU RESOURCES_LIMITS_MEMORY
    log_success "环境变量设置完成"
}

# 构建镜像
build_images() {
    log_info "构建 Docker 镜像..."
    
    # 获取 Git 提交哈希作为标签
    GIT_COMMIT=$(git rev-parse --short HEAD)
    IMAGE_TAG="${ENVIRONMENT}-${GIT_COMMIT}"
    
    # 构建 Java 服务
    log_info "构建 Java 服务..."
    mvn clean package -DskipTests
    
    # 构建各个服务的镜像
    services=("recommendation-service" "user-service" "content-service")
    for service in "${services[@]}"; do
        log_info "构建 ${service} 镜像..."
        docker build -f docker/Dockerfile.${service} -t ${DOCKER_REGISTRY}/${service}:${IMAGE_TAG} .
        docker tag ${DOCKER_REGISTRY}/${service}:${IMAGE_TAG} ${DOCKER_REGISTRY}/${service}:latest
    done
    
    # 构建 Python 服务
    python_services=("feature-service" "ranking-service")
    for service in "${python_services[@]}"; do
        log_info "构建 ${service} 镜像..."
        docker build -f docker/Dockerfile.${service} -t ${DOCKER_REGISTRY}/${service}:${IMAGE_TAG} .
        docker tag ${DOCKER_REGISTRY}/${service}:${IMAGE_TAG} ${DOCKER_REGISTRY}/${service}:latest
    done
    
    export IMAGE_TAG
    log_success "镜像构建完成"
}

# 推送镜像
push_images() {
    log_info "推送镜像到仓库..."
    
    all_services=("recommendation-service" "user-service" "content-service" "feature-service" "ranking-service")
    for service in "${all_services[@]}"; do
        log_info "推送 ${service} 镜像..."
        docker push ${DOCKER_REGISTRY}/${service}:${IMAGE_TAG}
        docker push ${DOCKER_REGISTRY}/${service}:latest
    done
    
    log_success "镜像推送完成"
}

# 创建命名空间
create_namespace() {
    log_info "创建命名空间 ${NAMESPACE}..."
    
    if kubectl get namespace ${NAMESPACE} &> /dev/null; then
        log_warning "命名空间 ${NAMESPACE} 已存在"
    else
        kubectl create namespace ${NAMESPACE}
        log_success "命名空间 ${NAMESPACE} 创建成功"
    fi
}

# 部署基础设施
deploy_infrastructure() {
    log_info "部署基础设施组件..."
    
    # 应用配置和密钥
    envsubst < k8s/configmap.yaml | kubectl apply -f -
    envsubst < k8s/secrets.yaml | kubectl apply -f -
    
    # 部署数据库和中间件
    if [ "$ENVIRONMENT" != "prod" ]; then
        log_info "部署开发/测试环境的数据库和中间件..."
        docker-compose -f docker-compose.${ENVIRONMENT}.yml up -d mysql redis elasticsearch clickhouse rabbitmq
    else
        log_info "生产环境使用外部数据库和中间件"
    fi
    
    log_success "基础设施部署完成"
}

# 部署应用服务
deploy_services() {
    log_info "部署应用服务..."
    
    # 替换环境变量并应用配置
    services=("recommendation-service" "user-service" "content-service")
    for service in "${services[@]}"; do
        log_info "部署 ${service}..."
        
        # 生成部署配置
        cat > /tmp/${service}-deployment.yaml << EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${service}
  namespace: ${NAMESPACE}
  labels:
    app: ${service}
    version: ${IMAGE_TAG}
spec:
  replicas: ${REPLICAS}
  selector:
    matchLabels:
      app: ${service}
  template:
    metadata:
      labels:
        app: ${service}
        version: ${IMAGE_TAG}
    spec:
      containers:
      - name: ${service}
        image: ${DOCKER_REGISTRY}/${service}:${IMAGE_TAG}
        ports:
        - containerPort: 8080
        resources:
          requests:
            cpu: ${RESOURCES_REQUESTS_CPU}
            memory: ${RESOURCES_REQUESTS_MEMORY}
          limits:
            cpu: ${RESOURCES_LIMITS_CPU}
            memory: ${RESOURCES_LIMITS_MEMORY}
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
EOF
        
        kubectl apply -f /tmp/${service}-deployment.yaml
        rm /tmp/${service}-deployment.yaml
    done
    
    # 等待部署完成
    log_info "等待服务启动..."
    for service in "${services[@]}"; do
        kubectl rollout status deployment/${service} -n ${NAMESPACE} --timeout=${KUBECTL_TIMEOUT}
    done
    
    log_success "应用服务部署完成"
}

# 部署网关和负载均衡
deploy_gateway() {
    log_info "部署 API 网关..."
    
    # 部署 Nginx Ingress
    cat > /tmp/nginx-ingress.yaml << EOF
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: recommendation-ingress
  namespace: ${NAMESPACE}
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/rate-limit: "100"
    nginx.ingress.kubernetes.io/rate-limit-window: "1m"
spec:
  rules:
  - host: api-${ENVIRONMENT}.recommendation.com
    http:
      paths:
      - path: /api/v1/recommend
        pathType: Prefix
        backend:
          service:
            name: recommendation-service
            port:
              number: 8080
      - path: /api/v1/users
        pathType: Prefix
        backend:
          service:
            name: user-service
            port:
              number: 8081
      - path: /api/v1/contents
        pathType: Prefix
        backend:
          service:
            name: content-service
            port:
              number: 8082
EOF
    
    kubectl apply -f /tmp/nginx-ingress.yaml
    rm /tmp/nginx-ingress.yaml
    
    log_success "API 网关部署完成"
}

# 部署监控
deploy_monitoring() {
    log_info "部署监控组件..."
    
    # 使用 Helm 部署 Prometheus 和 Grafana
    if command -v helm &> /dev/null; then
        # 添加 Helm 仓库
        helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
        helm repo add grafana https://grafana.github.io/helm-charts
        helm repo update
        
        # 部署 Prometheus
        helm upgrade --install prometheus prometheus-community/kube-prometheus-stack \
            --namespace ${NAMESPACE} \
            --set prometheus.prometheusSpec.retention=30d \
            --set grafana.adminPassword=admin123
        
        log_success "监控组件部署完成"
    else
        log_warning "Helm 未安装，跳过监控组件部署"
    fi
}

# 运行健康检查
health_check() {
    log_info "执行健康检查..."
    
    # 检查 Pod 状态
    kubectl get pods -n ${NAMESPACE}
    
    # 检查服务状态
    services=("recommendation-service" "user-service" "content-service")
    for service in "${services[@]}"; do
        log_info "检查 ${service} 健康状态..."
        
        # 等待服务就绪
        kubectl wait --for=condition=ready pod -l app=${service} -n ${NAMESPACE} --timeout=300s
        
        # 检查健康端点
        if kubectl get service ${service} -n ${NAMESPACE} &> /dev/null; then
            log_success "${service} 健康检查通过"
        else
            log_error "${service} 健康检查失败"
            return 1
        fi
    done
    
    log_success "所有服务健康检查通过"
}

# 回滚部署
rollback_deployment() {
    log_info "回滚部署..."
    
    services=("recommendation-service" "user-service" "content-service")
    for service in "${services[@]}"; do
        log_info "回滚 ${service}..."
        kubectl rollout undo deployment/${service} -n ${NAMESPACE}
        kubectl rollout status deployment/${service} -n ${NAMESPACE} --timeout=${KUBECTL_TIMEOUT}
    done
    
    log_success "回滚完成"
}

# 显示部署状态
show_status() {
    log_info "显示部署状态..."
    
    echo "=== 命名空间 ==="
    kubectl get namespace ${NAMESPACE}
    
    echo "=== Pods ==="
    kubectl get pods -n ${NAMESPACE} -o wide
    
    echo "=== Services ==="
    kubectl get services -n ${NAMESPACE}
    
    echo "=== Ingress ==="
    kubectl get ingress -n ${NAMESPACE}
    
    echo "=== 资源使用情况 ==="
    kubectl top pods -n ${NAMESPACE} 2>/dev/null || log_warning "Metrics Server 未安装，无法显示资源使用情况"
}

# 清理资源
cleanup() {
    log_info "清理部署资源..."
    
    read -p "确定要删除 ${NAMESPACE} 命名空间中的所有资源吗? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        kubectl delete namespace ${NAMESPACE}
        log_success "资源清理完成"
    else
        log_info "取消清理操作"
    fi
}

# 主函数
main() {
    log_info "开始部署智能内容推荐平台 - 环境: ${ENVIRONMENT}, 操作: ${ACTION}"
    
    check_dependencies
    setup_environment
    
    case $ACTION in
        deploy)
            create_namespace
            build_images
            push_images
            deploy_infrastructure
            deploy_services
            deploy_gateway
            deploy_monitoring
            health_check
            show_status
            log_success "部署完成! 访问地址: http://api-${ENVIRONMENT}.recommendation.com"
            ;;
        rollback)
            rollback_deployment
            health_check
            show_status
            ;;
        status)
            show_status
            ;;
        cleanup)
            cleanup
            ;;
        *)
            log_error "不支持的操作: $ACTION"
            echo "支持的操作: deploy, rollback, status, cleanup"
            exit 1
            ;;
    esac
}

# 执行主函数
main "$@"