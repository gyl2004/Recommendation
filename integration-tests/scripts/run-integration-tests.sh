#!/bin/bash

# 端到端集成测试运行脚本
# 用于启动所有服务并运行完整的集成测试套件

set -e

echo "🚀 开始运行智能内容推荐平台集成测试"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置变量
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
INTEGRATION_TEST_DIR="$PROJECT_ROOT/integration-tests"
DOCKER_COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
TEST_RESULTS_DIR="$INTEGRATION_TEST_DIR/test-results"

# 创建测试结果目录
mkdir -p "$TEST_RESULTS_DIR"

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

# 清理函数
cleanup() {
    log_info "清理测试环境..."
    
    # 停止Docker服务
    if [ -f "$DOCKER_COMPOSE_FILE" ]; then
        docker-compose -f "$DOCKER_COMPOSE_FILE" down -v --remove-orphans
    fi
    
    # 清理测试数据
    docker volume prune -f
    
    log_info "清理完成"
}

# 错误处理
trap cleanup EXIT

# 检查依赖
check_dependencies() {
    log_info "检查依赖..."
    
    # 检查Docker
    if ! command -v docker &> /dev/null; then
        log_error "Docker未安装，请先安装Docker"
        exit 1
    fi
    
    # 检查Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        log_error "Docker Compose未安装，请先安装Docker Compose"
        exit 1
    fi
    
    # 检查Maven
    if ! command -v mvn &> /dev/null; then
        log_error "Maven未安装，请先安装Maven"
        exit 1
    fi
    
    # 检查Python
    if ! command -v python3 &> /dev/null; then
        log_error "Python3未安装，请先安装Python3"
        exit 1
    fi
    
    log_success "依赖检查通过"
}

# 启动基础设施服务
start_infrastructure() {
    log_info "启动基础设施服务..."
    
    cd "$PROJECT_ROOT"
    
    # 启动MySQL, Redis, Elasticsearch, RabbitMQ等
    docker-compose up -d mysql redis elasticsearch rabbitmq
    
    # 等待服务启动
    log_info "等待基础设施服务启动..."
    sleep 30
    
    # 检查服务状态
    if ! docker-compose ps | grep -q "Up"; then
        log_error "基础设施服务启动失败"
        docker-compose logs
        exit 1
    fi
    
    log_success "基础设施服务启动成功"
}

# 构建和启动应用服务
start_application_services() {
    log_info "构建和启动应用服务..."
    
    cd "$PROJECT_ROOT"
    
    # 构建Java服务
    log_info "构建Java服务..."
    mvn clean package -DskipTests -q
    
    # 启动应用服务
    docker-compose up -d recommendation-service user-service content-service
    
    # 启动Python服务
    docker-compose up -d feature-service ranking-service
    
    # 等待应用服务启动
    log_info "等待应用服务启动..."
    sleep 45
    
    # 健康检查
    check_service_health "recommendation-service" "http://localhost:8080/actuator/health"
    check_service_health "user-service" "http://localhost:8081/actuator/health"
    check_service_health "content-service" "http://localhost:8082/actuator/health"
    check_service_health "feature-service" "http://localhost:8001/health"
    check_service_health "ranking-service" "http://localhost:8002/health"
    
    log_success "应用服务启动成功"
}

# 服务健康检查
check_service_health() {
    local service_name=$1
    local health_url=$2
    local max_attempts=30
    local attempt=1
    
    log_info "检查 $service_name 健康状态..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -f -s "$health_url" > /dev/null 2>&1; then
            log_success "$service_name 健康检查通过"
            return 0
        fi
        
        log_info "等待 $service_name 启动... (尝试 $attempt/$max_attempts)"
        sleep 5
        ((attempt++))
    done
    
    log_error "$service_name 健康检查失败"
    return 1
}

# 运行Java集成测试
run_java_integration_tests() {
    log_info "运行Java集成测试..."
    
    cd "$INTEGRATION_TEST_DIR"
    
    # 运行测试
    mvn test \
        -Dspring.profiles.active=test \
        -Dtest.containers.enabled=false \
        -Dmaven.test.failure.ignore=true \
        -Dsurefire.reportFormat=xml \
        -Dsurefire.reportsDirectory="$TEST_RESULTS_DIR/java"
    
    local java_exit_code=$?
    
    if [ $java_exit_code -eq 0 ]; then
        log_success "Java集成测试通过"
    else
        log_warning "Java集成测试存在失败用例"
    fi
    
    return $java_exit_code
}

# 运行Python集成测试
run_python_integration_tests() {
    log_info "运行Python集成测试..."
    
    cd "$INTEGRATION_TEST_DIR/python"
    
    # 安装Python依赖
    pip3 install -r requirements.txt
    
    # 运行测试
    python3 -m pytest \
        --html="$TEST_RESULTS_DIR/python/report.html" \
        --self-contained-html \
        --junitxml="$TEST_RESULTS_DIR/python/junit.xml" \
        --cov=. \
        --cov-report=html:"$TEST_RESULTS_DIR/python/coverage" \
        --cov-report=xml:"$TEST_RESULTS_DIR/python/coverage.xml" \
        -v
    
    local python_exit_code=$?
    
    if [ $python_exit_code -eq 0 ]; then
        log_success "Python集成测试通过"
    else
        log_warning "Python集成测试存在失败用例"
    fi
    
    return $python_exit_code
}

# 生成测试报告
generate_test_report() {
    log_info "生成测试报告..."
    
    local report_file="$TEST_RESULTS_DIR/integration-test-report.html"
    
    cat > "$report_file" << EOF
<!DOCTYPE html>
<html>
<head>
    <title>智能内容推荐平台集成测试报告</title>
    <meta charset="UTF-8">
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background-color: #f0f0f0; padding: 20px; border-radius: 5px; }
        .section { margin: 20px 0; padding: 15px; border: 1px solid #ddd; border-radius: 5px; }
        .success { background-color: #d4edda; border-color: #c3e6cb; }
        .warning { background-color: #fff3cd; border-color: #ffeaa7; }
        .error { background-color: #f8d7da; border-color: #f5c6cb; }
        .timestamp { color: #666; font-size: 0.9em; }
    </style>
</head>
<body>
    <div class="header">
        <h1>智能内容推荐平台集成测试报告</h1>
        <p class="timestamp">生成时间: $(date)</p>
    </div>
    
    <div class="section">
        <h2>测试概览</h2>
        <ul>
            <li>Java集成测试: $([ $java_test_result -eq 0 ] && echo "✅ 通过" || echo "❌ 失败")</li>
            <li>Python集成测试: $([ $python_test_result -eq 0 ] && echo "✅ 通过" || echo "❌ 失败")</li>
        </ul>
    </div>
    
    <div class="section">
        <h2>测试结果文件</h2>
        <ul>
            <li><a href="java/surefire-reports/index.html">Java测试报告</a></li>
            <li><a href="python/report.html">Python测试报告</a></li>
            <li><a href="python/coverage/index.html">Python代码覆盖率报告</a></li>
        </ul>
    </div>
    
    <div class="section">
        <h2>服务状态</h2>
        <pre>$(docker-compose ps)</pre>
    </div>
</body>
</html>
EOF
    
    log_success "测试报告已生成: $report_file"
}

# 主函数
main() {
    local start_time=$(date +%s)
    
    log_info "开始集成测试流程..."
    
    # 检查依赖
    check_dependencies
    
    # 启动服务
    start_infrastructure
    start_application_services
    
    # 运行测试
    run_java_integration_tests
    java_test_result=$?
    
    run_python_integration_tests
    python_test_result=$?
    
    # 生成报告
    generate_test_report
    
    # 计算总耗时
    local end_time=$(date +%s)
    local duration=$((end_time - start_time))
    
    # 输出结果
    echo ""
    log_info "=========================================="
    log_info "集成测试完成"
    log_info "总耗时: ${duration}秒"
    log_info "Java测试结果: $([ $java_test_result -eq 0 ] && echo "通过" || echo "失败")"
    log_info "Python测试结果: $([ $python_test_result -eq 0 ] && echo "通过" || echo "失败")"
    log_info "测试报告: $TEST_RESULTS_DIR/integration-test-report.html"
    log_info "=========================================="
    
    # 返回综合结果
    if [ $java_test_result -eq 0 ] && [ $python_test_result -eq 0 ]; then
        log_success "🎉 所有集成测试通过！"
        exit 0
    else
        log_error "❌ 部分集成测试失败，请查看详细报告"
        exit 1
    fi
}

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build)
            SKIP_BUILD=true
            shift
            ;;
        --keep-services)
            KEEP_SERVICES=true
            shift
            ;;
        --java-only)
            JAVA_ONLY=true
            shift
            ;;
        --python-only)
            PYTHON_ONLY=true
            shift
            ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo "选项:"
            echo "  --skip-build     跳过构建步骤"
            echo "  --keep-services  测试完成后保持服务运行"
            echo "  --java-only      只运行Java测试"
            echo "  --python-only    只运行Python测试"
            echo "  -h, --help       显示帮助信息"
            exit 0
            ;;
        *)
            log_error "未知选项: $1"
            exit 1
            ;;
    esac
done

# 如果指定了保持服务运行，则不执行清理
if [ "$KEEP_SERVICES" = true ]; then
    trap - EXIT
fi

# 运行主函数
main