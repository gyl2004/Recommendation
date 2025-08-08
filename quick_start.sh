#!/bin/bash

echo "🚀 启动智能电商推荐平台"
echo "=================================="

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到Java环境，请安装Java 11+"
    exit 1
fi

# 检查Maven环境
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误: 未找到Maven，请安装Maven 3.6+"
    exit 1
fi

# 检查Python环境
if ! command -v python3 &> /dev/null; then
    echo "❌ 错误: 未找到Python3，请安装Python 3.8+"
    exit 1
fi

echo "✅ 环境检查通过"

# 创建日志目录
mkdir -p logs

echo "📦 构建微服务..."

# 构建推荐服务
echo "构建推荐服务..."
cd simple-services/recommendation-service
mvn clean package -q
if [ $? -ne 0 ]; then
    echo "❌ 推荐服务构建失败"
    exit 1
fi
cd ../..

# 构建商品服务
echo "构建商品服务..."
cd simple-services/content-service
mvn clean package -q
if [ $? -ne 0 ]; then
    echo "❌ 商品服务构建失败"
    exit 1
fi
cd ../..

# 构建用户服务
echo "构建用户服务..."
cd simple-services/user-service
mvn clean package -q
if [ $? -ne 0 ]; then
    echo "❌ 用户服务构建失败"
    exit 1
fi
cd ../..

echo "✅ 所有服务构建完成"

# 停止可能运行的旧服务
echo "🔄 停止旧服务..."
pkill -f "java.*simple-.*-service" 2>/dev/null
pkill -f "python3.*http.server" 2>/dev/null

echo "🚀 启动微服务..."

# 启动推荐服务
echo "启动推荐服务 (端口8080)..."
cd simple-services/recommendation-service
nohup java -jar target/simple-recommendation-service-1.0.0.jar > ../../logs/recommendation.log 2>&1 &
RECOMMENDATION_PID=$!
cd ../..

# 启动商品服务
echo "启动商品服务 (端口8082)..."
cd simple-services/content-service
nohup java -jar target/simple-content-service-1.0.0.jar > ../../logs/products.log 2>&1 &
PRODUCTS_PID=$!
cd ../..

# 启动用户服务
echo "启动用户服务 (端口8081)..."
cd simple-services/user-service
nohup java -jar target/simple-user-service-1.0.0.jar > ../../logs/users.log 2>&1 &
USERS_PID=$!
cd ../..

# 启动前端服务
echo "启动前端服务 (端口3000)..."
cd web
nohup python3 -m http.server 3000 > ../logs/web-server.log 2>&1 &
WEB_PID=$!
cd ..

# 保存PID
echo $RECOMMENDATION_PID > logs/recommendation.pid
echo $PRODUCTS_PID > logs/products.pid
echo $USERS_PID > logs/users.pid
echo $WEB_PID > logs/web.pid

echo "⏳ 等待服务启动..."
sleep 15

echo "🔍 检查服务状态..."

# 检查推荐服务
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ 推荐服务 (8080) - 正常运行"
else
    echo "❌ 推荐服务 (8080) - 启动失败"
    echo "   查看日志: tail -f logs/recommendation.log"
fi

# 检查商品服务
if curl -s http://localhost:8082/actuator/health > /dev/null 2>&1; then
    echo "✅ 商品服务 (8082) - 正常运行"
else
    echo "❌ 商品服务 (8082) - 启动失败"
    echo "   查看日志: tail -f logs/products.log"
fi

# 检查用户服务
if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo "✅ 用户服务 (8081) - 正常运行"
else
    echo "❌ 用户服务 (8081) - 启动失败"
    echo "   查看日志: tail -f logs/users.log"
fi

# 检查前端服务
if curl -s http://localhost:3000/ > /dev/null 2>&1; then
    echo "✅ 前端服务 (3000) - 正常运行"
else
    echo "❌ 前端服务 (3000) - 启动失败"
    echo "   查看日志: tail -f logs/web-server.log"
fi

echo ""
echo "🎉 智能电商推荐平台启动完成！"
echo "=================================="
echo "📱 访问地址: http://localhost:3000/ecommerce.html"
echo ""
echo "🔧 管理命令:"
echo "  停止所有服务: ./stop_all.sh"
echo "  查看服务状态: ./check_status.sh"
echo "  查看日志: tail -f logs/服务名.log"
echo ""
echo "📊 API测试:"
echo "  推荐API: curl 'http://localhost:8080/api/v1/recommend/products?userId=123&size=5'"
echo "  商品API: curl 'http://localhost:8082/api/v1/products?page=0&size=5'"
echo "  用户API: curl 'http://localhost:8081/api/v1/users/123'"
echo ""
echo "🌟 开始你的智能购物之旅吧！"