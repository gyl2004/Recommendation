#!/bin/bash

echo "📊 智能电商推荐平台状态检查"
echo "=================================="

# 检查服务状态
check_service() {
    local name=$1
    local port=$2
    local health_url=$3
    
    if curl -s "$health_url" > /dev/null 2>&1; then
        echo "✅ $name ($port) - 正常运行"
        return 0
    else
        echo "❌ $name ($port) - 未运行"
        return 1
    fi
}

# 检查各个服务
check_service "推荐服务" "8080" "http://localhost:8080/actuator/health"
check_service "用户服务" "8081" "http://localhost:8081/actuator/health"
check_service "商品服务" "8082" "http://localhost:8082/actuator/health"

# 检查前端服务
if curl -s http://localhost:3000/ > /dev/null 2>&1; then
    echo "✅ 前端服务 (3000) - 正常运行"
else
    echo "❌ 前端服务 (3000) - 未运行"
fi

echo ""
echo "🔍 端口占用情况:"
for port in 8080 8081 8082 3000; do
    if lsof -i :$port > /dev/null 2>&1; then
        echo "  端口 $port: 已占用"
    else
        echo "  端口 $port: 空闲"
    fi
done

echo ""
echo "📊 API快速测试:"
echo "推荐API测试:"
curl -s "http://localhost:8080/api/v1/recommend/products?userId=123&size=2" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if data.get('success'):
        print('  ✅ 推荐API正常 - 返回', len(data.get('recommendations', [])), '个推荐')
    else:
        print('  ❌ 推荐API异常')
except:
    print('  ❌ 推荐API无响应')
" 2>/dev/null || echo "  ❌ 推荐API无响应"

echo "商品API测试:"
curl -s "http://localhost:8082/api/v1/products?page=0&size=2" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if data.get('success'):
        print('  ✅ 商品API正常 - 返回', len(data.get('products', [])), '个商品')
    else:
        print('  ❌ 商品API异常')
except:
    print('  ❌ 商品API无响应')
" 2>/dev/null || echo "  ❌ 商品API无响应"

echo "用户API测试:"
curl -s "http://localhost:8081/api/v1/users/123" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    if data.get('success'):
        print('  ✅ 用户API正常 - 用户ID:', data.get('userId'))
    else:
        print('  ❌ 用户API异常')
except:
    print('  ❌ 用户API无响应')
" 2>/dev/null || echo "  ❌ 用户API无响应"

echo ""
echo "🌐 访问地址:"
echo "  电商平台: http://localhost:3000/ecommerce.html"
echo ""
echo "📝 如有问题，请查看日志:"
echo "  推荐服务: tail -f logs/recommendation.log"
echo "  商品服务: tail -f logs/products.log"
echo "  用户服务: tail -f logs/users.log"
echo "  前端服务: tail -f logs/web-server.log"