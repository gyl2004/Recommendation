#!/bin/bash

# API网关管理脚本
set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
KONG_ADMIN_URL="http://localhost:8001"
NGINX_URL="http://localhost"

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

log_debug() {
    echo -e "${BLUE}[DEBUG]${NC} $1"
}

# 检查Kong连接
check_kong_connection() {
    if ! curl -s "$KONG_ADMIN_URL/status" > /dev/null; then
        log_error "无法连接到Kong Admin API: $KONG_ADMIN_URL"
        exit 1
    fi
}

# 显示Kong状态
show_kong_status() {
    log_info "Kong状态信息:"
    curl -s "$KONG_ADMIN_URL/status" | jq '.'
}

# 列出所有服务
list_services() {
    log_info "Kong服务列表:"
    curl -s "$KONG_ADMIN_URL/services" | jq '.data[] | {name: .name, host: .host, port: .port, protocol: .protocol}'
}

# 列出所有路由
list_routes() {
    log_info "Kong路由列表:"
    curl -s "$KONG_ADMIN_URL/routes" | jq '.data[] | {name: .name, paths: .paths, methods: .methods, service: .service.name}'
}

# 列出所有消费者
list_consumers() {
    log_info "Kong消费者列表:"
    curl -s "$KONG_ADMIN_URL/consumers" | jq '.data[] | {username: .username, custom_id: .custom_id, created_at: .created_at}'
}

# 列出所有插件
list_plugins() {
    log_info "Kong插件列表:"
    curl -s "$KONG_ADMIN_URL/plugins" | jq '.data[] | {name: .name, enabled: .enabled, service: .service, route: .route, consumer: .consumer}'
}

# 显示服务健康状态
show_health_status() {
    log_info "服务健康状态检查:"
    
    # 检查Nginx
    if curl -s "$NGINX_URL/health" | grep -q "healthy"; then
        log_info "✓ Nginx: 健康"
    else
        log_error "✗ Nginx: 不健康"
    fi
    
    # 检查Kong
    if curl -s "$KONG_ADMIN_URL/status" > /dev/null; then
        log_info "✓ Kong: 健康"
    else
        log_error "✗ Kong: 不健康"
    fi
    
    # 检查上游服务
    local upstreams=$(curl -s "$KONG_ADMIN_URL/upstreams" | jq -r '.data[].name')
    for upstream in $upstreams; do
        local health=$(curl -s "$KONG_ADMIN_URL/upstreams/$upstream/health" | jq -r '.data[].health')
        if [ "$health" = "HEALTHY" ]; then
            log_info "✓ Upstream $upstream: 健康"
        else
            log_warn "⚠ Upstream $upstream: $health"
        fi
    done
}

# 显示实时指标
show_metrics() {
    log_info "实时指标:"
    
    # Kong指标
    if command -v curl &> /dev/null && command -v jq &> /dev/null; then
        local kong_metrics=$(curl -s "$KONG_ADMIN_URL/metrics")
        echo "Kong连接数: $(echo "$kong_metrics" | grep -o 'kong_nginx_http_current_connections [0-9]*' | awk '{print $2}')"
        echo "Kong请求总数: $(echo "$kong_metrics" | grep -o 'kong_http_requests_total [0-9]*' | awk '{print $2}')"
    fi
    
    # 系统指标
    echo "系统负载: $(uptime | awk -F'load average:' '{print $2}')"
    echo "内存使用: $(free -h | awk '/^Mem:/ {print $3 "/" $2}')"
    echo "磁盘使用: $(df -h / | awk 'NR==2 {print $3 "/" $2 " (" $5 ")"}')"
}

# 重新加载Kong配置
reload_kong_config() {
    log_info "重新加载Kong配置..."
    
    if curl -s -X POST "$KONG_ADMIN_URL/config" -F config=@kong/kong.yml; then
        log_info "Kong配置重新加载成功"
    else
        log_error "Kong配置重新加载失败"
        exit 1
    fi
}

# 重新加载Nginx配置
reload_nginx_config() {
    log_info "重新加载Nginx配置..."
    
    # 测试配置
    if docker-compose exec nginx nginx -t; then
        # 重新加载
        if docker-compose exec nginx nginx -s reload; then
            log_info "Nginx配置重新加载成功"
        else
            log_error "Nginx配置重新加载失败"
            exit 1
        fi
    else
        log_error "Nginx配置测试失败"
        exit 1
    fi
}

# 添加服务
add_service() {
    local name=$1
    local url=$2
    
    if [ -z "$name" ] || [ -z "$url" ]; then
        log_error "用法: add_service <service_name> <service_url>"
        exit 1
    fi
    
    log_info "添加服务: $name -> $url"
    
    local response=$(curl -s -X POST "$KONG_ADMIN_URL/services" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"$name\",\"url\":\"$url\"}")
    
    if echo "$response" | jq -e '.id' > /dev/null; then
        log_info "服务添加成功"
        echo "$response" | jq '.'
    else
        log_error "服务添加失败"
        echo "$response" | jq '.'
        exit 1
    fi
}

# 添加路由
add_route() {
    local service_name=$1
    local path=$2
    
    if [ -z "$service_name" ] || [ -z "$path" ]; then
        log_error "用法: add_route <service_name> <path>"
        exit 1
    fi
    
    log_info "为服务 $service_name 添加路由: $path"
    
    local response=$(curl -s -X POST "$KONG_ADMIN_URL/services/$service_name/routes" \
        -H "Content-Type: application/json" \
        -d "{\"paths\":[\"$path\"]}")
    
    if echo "$response" | jq -e '.id' > /dev/null; then
        log_info "路由添加成功"
        echo "$response" | jq '.'
    else
        log_error "路由添加失败"
        echo "$response" | jq '.'
        exit 1
    fi
}

# 启用插件
enable_plugin() {
    local plugin_name=$1
    local service_name=$2
    local config=$3
    
    if [ -z "$plugin_name" ]; then
        log_error "用法: enable_plugin <plugin_name> [service_name] [config_json]"
        exit 1
    fi
    
    log_info "启用插件: $plugin_name"
    
    local url="$KONG_ADMIN_URL/plugins"
    local data="{\"name\":\"$plugin_name\""
    
    if [ -n "$service_name" ]; then
        data="$data,\"service\":{\"name\":\"$service_name\"}"
    fi
    
    if [ -n "$config" ]; then
        data="$data,\"config\":$config"
    fi
    
    data="$data}"
    
    local response=$(curl -s -X POST "$url" \
        -H "Content-Type: application/json" \
        -d "$data")
    
    if echo "$response" | jq -e '.id' > /dev/null; then
        log_info "插件启用成功"
        echo "$response" | jq '.'
    else
        log_error "插件启用失败"
        echo "$response" | jq '.'
        exit 1
    fi
}

# 查看日志
view_logs() {
    local service=${1:-}
    local lines=${2:-100}
    
    if [ -n "$service" ]; then
        log_info "查看 $service 服务日志 (最近 $lines 行):"
        docker-compose logs --tail="$lines" -f "$service"
    else
        log_info "查看所有服务日志 (最近 $lines 行):"
        docker-compose logs --tail="$lines" -f
    fi
}

# 性能测试
run_performance_test() {
    local url=${1:-"$NGINX_URL/health"}
    local concurrent=${2:-10}
    local requests=${3:-100}
    
    log_info "运行性能测试:"
    log_info "URL: $url"
    log_info "并发数: $concurrent"
    log_info "请求数: $requests"
    
    if command -v ab &> /dev/null; then
        ab -n "$requests" -c "$concurrent" "$url"
    elif command -v wrk &> /dev/null; then
        wrk -t"$concurrent" -c"$concurrent" -d10s "$url"
    else
        log_error "需要安装 apache2-utils (ab) 或 wrk 工具"
        exit 1
    fi
}

# 备份配置
backup_config() {
    local backup_dir="backups/$(date +%Y%m%d_%H%M%S)"
    
    log_info "备份配置到: $backup_dir"
    
    mkdir -p "$backup_dir"
    
    # 备份Kong配置
    curl -s "$KONG_ADMIN_URL/config" > "$backup_dir/kong-config.json"
    cp kong/kong.yml "$backup_dir/"
    
    # 备份Nginx配置
    cp nginx/nginx.conf "$backup_dir/"
    cp -r nginx/conf.d "$backup_dir/" 2>/dev/null || true
    
    # 备份Docker Compose配置
    cp docker-compose.yml "$backup_dir/"
    
    log_info "配置备份完成"
}

# 显示帮助信息
show_help() {
    echo "API网关管理脚本"
    echo
    echo "用法: $0 <command> [options]"
    echo
    echo "命令:"
    echo "  status              - 显示Kong状态"
    echo "  services            - 列出所有服务"
    echo "  routes              - 列出所有路由"
    echo "  consumers           - 列出所有消费者"
    echo "  plugins             - 列出所有插件"
    echo "  health              - 显示服务健康状态"
    echo "  metrics             - 显示实时指标"
    echo "  reload-kong         - 重新加载Kong配置"
    echo "  reload-nginx        - 重新加载Nginx配置"
    echo "  add-service <name> <url>        - 添加服务"
    echo "  add-route <service> <path>      - 添加路由"
    echo "  enable-plugin <name> [service] [config] - 启用插件"
    echo "  logs [service] [lines]          - 查看日志"
    echo "  test [url] [concurrent] [requests] - 性能测试"
    echo "  backup              - 备份配置"
    echo "  help                - 显示帮助信息"
}

# 主函数
main() {
    local command=${1:-help}
    
    case "$command" in
        "status")
            check_kong_connection
            show_kong_status
            ;;
        "services")
            check_kong_connection
            list_services
            ;;
        "routes")
            check_kong_connection
            list_routes
            ;;
        "consumers")
            check_kong_connection
            list_consumers
            ;;
        "plugins")
            check_kong_connection
            list_plugins
            ;;
        "health")
            show_health_status
            ;;
        "metrics")
            show_metrics
            ;;
        "reload-kong")
            check_kong_connection
            reload_kong_config
            ;;
        "reload-nginx")
            reload_nginx_config
            ;;
        "add-service")
            check_kong_connection
            add_service "$2" "$3"
            ;;
        "add-route")
            check_kong_connection
            add_route "$2" "$3"
            ;;
        "enable-plugin")
            check_kong_connection
            enable_plugin "$2" "$3" "$4"
            ;;
        "logs")
            view_logs "$2" "$3"
            ;;
        "test")
            run_performance_test "$2" "$3" "$4"
            ;;
        "backup")
            backup_config
            ;;
        "help"|*)
            show_help
            ;;
    esac
}

# 执行主函数
main "$@"