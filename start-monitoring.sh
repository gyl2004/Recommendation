#!/bin/bash

# 推荐系统监控启动脚本

echo "启动推荐系统监控服务..."

# 创建必要的目录
mkdir  monitoring/alertmanager
mkdir  monitoring/grafana/provisioning/datasources
mkdir  monitoring/grafana/provisioning/dashboards
mkdir  monitoring/grafana/dashboards

# 检查Docker是否运行
if ! docker info > /dev/null 2>&1; then
    echo "错误: Docker未运行，请先启动Docker"
    exit 1
fi

# 检查Docker Compose是否安装
if ! command -v docker-compose &> /dev/null; then
    echo "错误: Docker Compose未安装"
    exit 1
fi

# 启动监控服务
echo "启动Prometheus、Grafana和相关监控服务..."
docker-compose -f monitoring-docker-compose.yml up -d

# 等待服务启动
echo "等待服务启动..."
sleep 30

# 检查服务状态
echo "检查服务状态..."
docker-compose -f monitoring-docker-compose.yml ps

echo ""
echo "监控服务启动完成！"
echo ""
echo "访问地址:"
echo "- Prometheus: http://localhost:9090"
echo "- Grafana: http://localhost:3000 (admin/admin123)"
echo "- AlertManager: http://localhost:9093"
echo ""
echo "推荐服务监控端点:"
echo "- 健康检查: http://localhost:8080/actuator/health"
echo "- Prometheus指标: http://localhost:8080/actuator/prometheus"
echo "- 自定义监控API: http://localhost:8080/api/v1/monitoring/health"
echo ""

# 显示Grafana仪表板导入说明
echo "Grafana仪表板配置:"
echo "1. 访问 http://localhost:3000"
echo "2. 使用 admin/admin123 登录"
echo "3. 仪表板已自动配置，可在Dashboards中查看"
echo ""

echo "监控系统已就绪！"