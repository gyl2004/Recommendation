#!/bin/bash
# 启动Java应用服务脚本

echo "=========================================="
echo "启动Java应用服务"
echo "=========================================="

# 检查Java和Maven环境
echo "[INFO] 检查Java环境..."
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java未安装，正在安装..."
    sudo apt update
    sudo apt install -y openjdk-11-jdk
fi

echo "[INFO] Java版本:"
java -version

echo "[INFO] 检查Maven环境..."
if ! command -v mvn &> /dev/null; then
    echo "[ERROR] Maven未安装，正在安装..."
    sudo apt update
    sudo apt install -y maven
fi

echo "[INFO] Maven版本:"
mvn -version

# 检查基础设施服务状态
echo "[INFO] 检查基础设施服务状态..."

# 检查MySQL
if docker-compose exec -T mysql mysqladmin -uroot -proot123 ping > /dev/null 2>&1; then
    echo "[SUCCESS] MySQL连接正常"
else
    echo "[ERROR] MySQL连接失败"
    exit 1
fi

# 检查Redis
if docker-compose exec -T redis redis-cli ping > /dev/null 2>&1; then
    echo "[SUCCESS] Redis连接正常"
else
    echo "[ERROR] Redis连接失败"
    exit 1
fi

# 检查Elasticsearch
if curl -s http://localhost:9200/_cluster/health > /dev/null 2>&1; then
    echo "[SUCCESS] Elasticsearch连接正常"
else
    echo "[ERROR] Elasticsearch连接失败"
    exit 1
fi

# 初始化数据库
echo "[INFO] 初始化数据库..."
if [ -f "init_test_data.sql" ]; then
    docker-compose exec -T mysql mysql -uroot -proot123 recommendation_platform < init_test_data.sql
    echo "[SUCCESS] 测试数据初始化完成"
fi

# 构建Java项目
echo "[INFO] 构建Java项目..."
mvn clean package -DskipTests -q

if [ $? -ne 0 ]; then
    echo "[ERROR] Maven构建失败"
    exit 1
fi

echo "[SUCCESS] Java项目构建完成"

# 创建日志目录
mkdir -p logs

# 启动推荐服务
echo "[INFO] 启动推荐服务 (端口8080)..."
cd recommendation-service
nohup mvn spring-boot:run -Dspring-boot.run.profiles=dev \
    -Dspring-boot.run.jvmArguments="-Xms512m -Xmx1g" \
    > ../logs/recommendation-service.log 2>&1 &
echo $! > ../logs/recommendation-service.pid
cd ..

# 等待推荐服务启动
echo "[INFO] 等待推荐服务启动..."
sleep 30

# 启动用户服务
echo "[INFO] 启动用户服务 (端口8081)..."
cd user-service
nohup mvn spring-boot:run -Dspring-boot.run.profiles=dev \
    -Dserver.port=8081 \
    -Dspring-boot.run.jvmArguments="-Xms256m -Xmx512m" \
    > ../logs/user-service.log 2>&1 &
echo $! > ../logs/user-service.pid
cd ..

# 等待用户服务启动
echo "[INFO] 等待用户服务启动..."
sleep 20

# 启动内容服务
echo "[INFO] 启动内容服务 (端口8082)..."
cd content-service
nohup mvn spring-boot:run -Dspring-boot.run.profiles=dev \
    -Dserver.port=8082 \
    -Dspring-boot.run.jvmArguments="-Xms256m -Xmx512m" \
    > ../logs/content-service.log 2>&1 &
echo $! > ../logs/content-service.pid
cd ..

# 等待所有服务启动
echo "[INFO] 等待所有服务启动完成..."
sleep 60

# 检查服务状态
echo "=========================================="
echo "检查服务状态"
echo "=========================================="

# 检查推荐服务
echo "[INFO] 检查推荐服务..."
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "[SUCCESS] 推荐服务启动成功 - http://localhost:8080"
else
    echo "[ERROR] 推荐服务启动失败"
    echo "查看日志: tail -f logs/recommendation-service.log"
fi

# 检查用户服务
echo "[INFO] 检查用户服务..."
if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo "[SUCCESS] 用户服务启动成功 - http://localhost:8081"
else
    echo "[ERROR] 用户服务启动失败"
    echo "查看日志: tail -f logs/user-service.log"
fi

# 检查内容服务
echo "[INFO] 检查内容服务..."
if curl -s http://localhost:8082/actuator/health > /dev/null 2>&1; then
    echo "[SUCCESS] 内容服务启动成功 - http://localhost:8082"
else
    echo "[ERROR] 内容服务启动失败"
    echo "查看日志: tail -f logs/content-service.log"
fi

# 检查特征服务
echo "[INFO] 检查特征服务..."
if curl -s http://localhost:8003/health > /dev/null 2>&1; then
    echo "[SUCCESS] 特征服务运行正常 - http://localhost:8003"
else
    echo "[WARNING] 特征服务可能有问题"
fi

echo "=========================================="
echo "服务启动完成！"
echo "=========================================="
echo ""
echo "🎉 所有服务状态:"
echo "- MySQL数据库: localhost:3306 (root/root123)"
echo "- Redis缓存: localhost:6379"
echo "- Elasticsearch: http://localhost:9200"
echo "- RabbitMQ管理: http://localhost:15672 (admin/admin123)"
echo "- ClickHouse: http://localhost:8123"
echo ""
echo "🚀 应用服务:"
echo "- 推荐服务: http://localhost:8080"
echo "- 用户服务: http://localhost:8081"
echo "- 内容服务: http://localhost:8082"
echo "- 特征服务: http://localhost:8003"
echo ""
echo "📊 测试API:"
echo "curl http://localhost:8080/actuator/health"
echo "curl \"http://localhost:8080/api/v1/recommend/content?userId=1&size=10\""
echo "curl http://localhost:8081/api/v1/users/1"
echo "curl \"http://localhost:8082/api/v1/contents/search?query=AI\""
echo ""
echo "📝 查看日志:"
echo "tail -f logs/recommendation-service.log"
echo "tail -f logs/user-service.log"
echo "tail -f logs/content-service.log"
echo ""
echo "🛑 停止服务:"
echo "kill \$(cat logs/*.pid)"
echo "docker-compose down"
echo ""

# 显示进程信息
echo "📋 运行中的Java进程:"
ps aux | grep java | grep -v grep

echo ""
echo "✅ 启动脚本执行完成！"