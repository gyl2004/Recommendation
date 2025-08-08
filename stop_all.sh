#!/bin/bash

echo "🛑 停止智能电商推荐平台"
echo "=================================="

# 停止Java服务
echo "停止微服务..."
pkill -f "java.*simple-.*-service"

# 停止前端服务
echo "停止前端服务..."
pkill -f "python3.*http.server"

# 清理PID文件
rm -f logs/*.pid

echo "✅ 所有服务已停止"

# 显示端口状态
echo ""
echo "🔍 端口状态检查:"
for port in 8080 8081 8082 3000; do
    if lsof -i :$port > /dev/null 2>&1; then
        echo "  端口 $port: 仍在使用"
    else
        echo "  端口 $port: 已释放"
    fi
done

echo ""
echo "📝 提示:"
echo "  如果端口仍被占用，请手动终止进程"
echo "  查看占用进程: lsof -i :端口号"
echo "  强制终止: kill -9 进程ID"