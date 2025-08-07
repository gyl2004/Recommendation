#!/bin/bash

# API网关部署脚本
set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查依赖
check_dependencies() {
    log_info "检查依赖..."
    
    if ! command -v docker &> /dev/null; then
        log_error "Docker未安装"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose未安装"
        exit 1
    fi
    
    log_info "依赖检查完成"
}

# 创建必要的目录
create_directories() {
    log_info "创建必要的目录..."
    
    mkdir -p logs/nginx
    mkdir -p logs/kong
    mkdir -p nginx/conf.d
    mkdir -p nginx/ssl
    mkdir -p static
    mkdir -p redis
    
    log_info "目录创建完成"
}

# 生成SSL证书（自签名，生产环境请使用正式证书）
generate_ssl_certificates() {
    log_info "生成SSL证书..."
    
    if [ ! -f "nginx/ssl/server.crt" ]; then
        openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
            -keyout nginx/ssl/server.key \
            -out nginx/ssl/server.crt \
            -subj "/C=CN/ST=Beijing/L=Beijing/O=Recommendation/OU=IT/CN=api.recommendation.com"
        
        log_info "SSL证书生成完成"
    else
        log_info "SSL证书已存在，跳过生成"
    fi
}

# 创建Redis配置
create_redis_config() {
    log_info "创建Redis配置..."
    
    cat > redis/redis.conf << EOF
# Redis配置文件
bind 0.0.0.0
port 6379
timeout 0
tcp-keepalive 300

# 内存配置
maxmemory 256mb
maxmemory-policy allkeys-lru

# 持久化配置
save 900 1
save 300 10
save 60 10000

# 日志配置
loglevel notice
logfile ""

# 安全配置
# requirepass your-redis-password-here

# 网络配置
tcp-backlog 511
databases 16
EOF
    
    log_info "Redis配置创建完成"
}

# 创建Nginx额外配置
create_nginx_extra_config() {
    log_info "创建Nginx额外配置..."
    
    cat > nginx/conf.d/api-routes.conf << EOF
# API路由额外配置
location /api/v1/recommend {
    limit_req zone=api_limit burst=20 nodelay;
    
    proxy_pass http://recommendation_service;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    
    # 超时设置
    proxy_connect_timeout 5s;
    proxy_send_timeout 10s;
    proxy_read_timeout 10s;
    
    # 缓存设置
    proxy_cache_bypass \$http_pragma;
    proxy_cache_revalidate on;
}

location /api/v1/users {
    limit_req zone=api_limit burst=10 nodelay;
    
    proxy_pass http://user_service;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    
    proxy_connect_timeout 5s;
    proxy_send_timeout 10s;
    proxy_read_timeout 10s;
}

location /api/v1/contents {
    limit_req zone=api_limit burst=15 nodelay;
    
    proxy_pass http://content_service;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_Set_header X-Forwarded-Proto \$scheme;
    
    proxy_connect_timeout 5s;
    proxy_send_timeout 15s;
    proxy_read_timeout 15s;
}

location /api/v1/features {
    limit_req zone=api_limit burst=30 nodelay;
    
    proxy_pass http://feature_service;
    proxy_set_header Host \$host;
    proxy_set_header X-Real-IP \$remote_addr;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    
    proxy_connect_timeout 3s;
    proxy_send_timeout 8s;
    proxy_read_timeout 8s;
}
EOF
    
    log_info "Nginx额外配置创建完成"
}

# 验证配置文件
validate_configs() {
    log_info "验证配置文件..."
    
    # 验证Nginx配置
    if docker run --rm -v "$(pwd)/nginx/nginx.conf:/etc/nginx/nginx.conf:ro" nginx:1.21-alpine nginx -t; then
        log_info "Nginx配置验证通过"
    else
        log_error "Nginx配置验证失败"
        exit 1
    fi
    
    # 验证Kong配置
    if docker run --rm -v "$(pwd)/kong/kong.yml:/kong/declarative/kong.yml:ro" \
        -e "KONG_DATABASE=off" \
        -e "KONG_DECLARATIVE_CONFIG=/kong/declarative/kong.yml" \
        kong:3.4-alpine kong config parse /kong/declarative/kong.yml; then
        log_info "Kong配置验证通过"
    else
        log_error "Kong配置验证失败"
        exit 1
    fi
}

# 启动服务
start_services() {
    log_info "启动API网关服务..."
    
    # 创建网络
    docker network create recommendation_backend 2>/dev/null || true
    
    # 启动服务
    docker-compose up -d
    
    log_info "等待服务启动..."
    sleep 30
    
    # 检查服务状态
    check_service_health
}

# 检查服务健康状态
check_service_health() {
    log_info "检查服务健康状态..."
    
    local services=("nginx:80" "kong:8000" "kong:8001" "redis-kong:6379")
    local failed_services=()
    
    for service in "${services[@]}"; do
        local name=$(echo $service | cut -d: -f1)
        local port=$(echo $service | cut -d: -f2)
        
        if docker-compose exec -T $name nc -z localhost $port 2>/dev/null; then
            log_info "$name 服务健康"
        else
            log_error "$name 服务不健康"
            failed_services+=($name)
        fi
    done
    
    if [ ${#failed_services[@]} -eq 0 ]; then
        log_info "所有服务健康检查通过"
    else
        log_error "以下服务健康检查失败: ${failed_services[*]}"
        exit 1
    fi
}

# 配置Kong
configure_kong() {
    log_info "配置Kong..."
    
    # 等待Kong启动
    local max_attempts=30
    local attempt=1
    
    while [ $attempt -le $max_attempts ]; do
        if curl -s http://localhost:8001/status > /dev/null; then
            log_info "Kong已启动"
            break
        fi
        
        log_info "等待Kong启动... ($attempt/$max_attempts)"
        sleep 2
        ((attempt++))
    done
    
    if [ $attempt -gt $max_attempts ]; then
        log_error "Kong启动超时"
        exit 1
    fi
    
    # 重新加载配置
    if curl -s -X POST http://localhost:8001/config -F config=@kong/kong.yml; then
        log_info "Kong配置加载成功"
    else
        log_error "Kong配置加载失败"
        exit 1
    fi
}

# 运行测试
run_tests() {
    log_info "运行API网关测试..."
    
    # 测试Nginx健康检查
    if curl -s http://localhost/health | grep -q "healthy"; then
        log_info "Nginx健康检查通过"
    else
        log_error "Nginx健康检查失败"
        exit 1
    fi
    
    # 测试Kong健康检查
    if curl -s http://localhost:8001/status | grep -q "database"; then
        log_info "Kong健康检查通过"
    else
        log_error "Kong健康检查失败"
        exit 1
    fi
    
    log_info "所有测试通过"
}

# 显示部署信息
show_deployment_info() {
    log_info "部署完成！"
    echo
    echo "服务访问地址:"
    echo "  - Nginx (HTTP):  http://localhost"
    echo "  - Nginx (HTTPS): https://localhost"
    echo "  - Kong Proxy:    http://localhost:8000"
    echo "  - Kong Admin:    http://localhost:8001"
    echo "  - Kong Manager:  http://localhost:8002"
    echo "  - Prometheus:    http://localhost:9090"
    echo "  - Grafana:       http://localhost:3000 (admin/admin123)"
    echo
    echo "日志查看:"
    echo "  docker-compose logs -f [service_name]"
    echo
    echo "服务管理:"
    echo "  启动: docker-compose up -d"
    echo "  停止: docker-compose down"
    echo "  重启: docker-compose restart [service_name]"
}

# 主函数
main() {
    log_info "开始部署API网关..."
    
    check_dependencies
    create_directories
    generate_ssl_certificates
    create_redis_config
    create_nginx_extra_config
    validate_configs
    start_services
    configure_kong
    run_tests
    show_deployment_info
    
    log_info "API网关部署完成！"
}

# 处理命令行参数
case "${1:-deploy}" in
    "deploy")
        main
        ;;
    "start")
        docker-compose up -d
        ;;
    "stop")
        docker-compose down
        ;;
    "restart")
        docker-compose restart
        ;;
    "logs")
        docker-compose logs -f "${2:-}"
        ;;
    "health")
        check_service_health
        ;;
    "test")
        run_tests
        ;;
    *)
        echo "用法: $0 {deploy|start|stop|restart|logs|health|test}"
        echo "  deploy  - 完整部署API网关"
        echo "  start   - 启动服务"
        echo "  stop    - 停止服务"
        echo "  restart - 重启服务"
        echo "  logs    - 查看日志"
        echo "  health  - 健康检查"
        echo "  test    - 运行测试"
        exit 1
        ;;
esac