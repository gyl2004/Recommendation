#!/bin/bash

echo "=========================================="
echo "检查服务状态"
echo "=========================================="

# 检查推荐服务
echo -n "推荐服务 (8080): "
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ 正常"
    curl -s http://localhost:8080/actuator/health | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8080/actuator/health
else
    echo "❌ 异常"
fi

echo ""

# 检查用户服务
echo -n "用户服务 (8081): "
if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo "✅ 正常"
    curl -s http://localhost:8081/actuator/health | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8081/actuator/health
else
    echo "❌ 异常"
fi

echo ""

# 检查内容服务
echo -n "内容服务 (8082): "
if curl -s http://localhost:8082/actuator/health > /dev/null 2>&1; then
    echo "✅ 正常"
    curl -s http://localhost:8082/actuator/health | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8082/actuator/health
else
    echo "❌ 异常"
fi

echo ""

# 检查特征服务（Python）
echo -n "特征服务 (8003): "
if curl -s http://localhost:8003/health > /dev/null 2>&1; then
    echo "✅ 正常"
    curl -s http://localhost:8003/health | python3 -m json.tool 2>/dev/null || curl -s http://localhost:8003/health
else
    echo "❌ 异常"
fi

echo ""
echo "=========================================="
echo "进程信息"
echo "=========================================="
ps aux | grep -E "(java.*App|python.*main)" | grep -v grep