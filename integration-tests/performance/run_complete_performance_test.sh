#!/bin/bash

# 智能内容推荐平台完整性能测试和优化脚本

set -e

# 配置参数
BASE_URL=${BASE_URL:-"http://localhost:8080"}
PERFORMANCE_DIR="$(dirname "$0")"
RESULTS_DIR="$PERFORMANCE_DIR/results"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# 创建结果目录
mkdir -p "$RESULTS_DIR"

echo "=========================================="
echo "智能内容推荐平台完整性能测试"
echo "=========================================="
echo "测试时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "测试目标: $BASE_URL"
echo "结果目录: $RESULTS_DIR"
echo "=========================================="

# 检查依赖
check_dependencies() {
    echo "🔍 检查测试依赖..."
    
    # 检查JMeter
    if ! command -v jmeter &> /dev/null; then
        echo "❌ JMeter未安装，请先安装JMeter"
        exit 1
    fi
    
    # 检查Python依赖
    if ! python3 -c "import requests, psutil, mysql.connector, redis" 2>/dev/null; then
        echo "❌ Python依赖缺失，正在安装..."
        pip3 install requests psutil mysql-connector-python redis matplotlib pandas
    fi
    
    # 检查服务可用性
    if ! curl -f -s "$BASE_URL/actuator/health" > /dev/null; then
        echo "⚠️ 警告: 服务可能未启动"
        echo "请确保以下服务正在运行:"
        echo "- 推荐服务 (端口8080)"
        echo "- MySQL数据库 (端口3306)"
        echo "- Redis缓存 (端口6379)"
        echo "- Elasticsearch (端口9200)"
        read -p "是否继续测试? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
    
    echo "✅ 依赖检查完成"
}

# 系统预热
warm_up_system() {
    echo "🔥 系统预热..."
    
    # 预热推荐服务
    for i in {1..50}; do
        curl -s "$BASE_URL/api/v1/recommend/content?userId=warmup_$i&size=10" > /dev/null || true
    done
    
    # 等待系统稳定
    sleep 10
    
    echo "✅ 系统预热完成"
}

# 执行基准性能测试
run_baseline_test() {
    echo "📊 执行基准性能测试..."
    
    # 使用JMeter执行基准测试
    jmeter -n \
        -t "$PERFORMANCE_DIR/jmeter/recommendation_load_test.jmx" \
        -l "$RESULTS_DIR/baseline_test_$TIMESTAMP.jtl" \
        -e \
        -o "$RESULTS_DIR/baseline-report-$TIMESTAMP" \
        -Jbase_url="$BASE_URL" \
        -Jusers=500 \
        -Jramp_time=30 \
        -Jduration=120 \
        > "$RESULTS_DIR/baseline_test_$TIMESTAMP.log" 2>&1
    
    echo "✅ 基准测试完成"
}

# 执行目标性能测试
run_target_performance_test() {
    echo "🎯 执行目标性能测试 (10000 QPS)..."
    
    # 启动系统监控
    "$PERFORMANCE_DIR/scripts/monitor_system.sh" 300 &
    MONITOR_PID=$!
    
    # 执行高负载测试
    jmeter -n \
        -t "$PERFORMANCE_DIR/jmeter/recommendation_load_test.jmx" \
        -l "$RESULTS_DIR/target_test_$TIMESTAMP.jtl" \
        -e \
        -o "$RESULTS_DIR/target-report-$TIMESTAMP" \
        -Jbase_url="$BASE_URL" \
        -Jusers=1000 \
        -Jramp_time=60 \
        -Jduration=300 \
        > "$RESULTS_DIR/target_test_$TIMESTAMP.log" 2>&1
    
    # 停止监控
    kill $MONITOR_PID 2>/dev/null || true
    
    echo "✅ 目标性能测试完成"
}

# 分析测试结果
analyze_results() {
    echo "📈 分析测试结果..."
    
    # 分析基准测试结果
    if [ -f "$RESULTS_DIR/baseline_test_$TIMESTAMP.jtl" ]; then
        echo "分析基准测试结果..."
        python3 "$PERFORMANCE_DIR/scripts/analyze_results.py" \
            "$RESULTS_DIR/baseline_test_$TIMESTAMP.jtl"
    fi
    
    # 分析目标测试结果
    if [ -f "$RESULTS_DIR/target_test_$TIMESTAMP.jtl" ]; then
        echo "分析目标测试结果..."
        python3 "$PERFORMANCE_DIR/scripts/analyze_results.py" \
            "$RESULTS_DIR/target_test_$TIMESTAMP.jtl"
    fi
    
    echo "✅ 结果分析完成"
}

# 执行瓶颈分析
run_bottleneck_analysis() {
    echo "🔍 执行系统瓶颈分析..."
    
    python3 "$PERFORMANCE_DIR/scripts/bottleneck_analysis.py" \
        "$RESULTS_DIR/target_test_$TIMESTAMP.jtl"
    
    echo "✅ 瓶颈分析完成"
}

# 验证性能目标
validate_performance_targets() {
    echo "🎯 验证性能目标..."
    
    python3 "$PERFORMANCE_DIR/scripts/performance_validation.py" "$BASE_URL"
    
    echo "✅ 性能目标验证完成"
}

# 生成优化建议
generate_optimization_recommendations() {
    echo "💡 生成优化建议..."
    
    cat > "$RESULTS_DIR/optimization_summary_$TIMESTAMP.md" << EOF
# 智能内容推荐平台性能优化总结

## 测试概述
- 测试时间: $(date '+%Y-%m-%d %H:%M:%S')
- 测试目标: 10,000 QPS, 500ms响应时间, 99.9%可用性
- 测试结果目录: $RESULTS_DIR

## 优化建议优先级

### 🚨 紧急优化 (立即执行)
1. **JVM参数调优**
   - 增加堆内存: -Xms4g -Xmx4g
   - 使用G1垃圾回收器: -XX:+UseG1GC
   - 优化GC参数: -XX:MaxGCPauseMillis=200

2. **数据库连接池优化**
   - 增加最大连接数: maximum-pool-size: 50
   - 优化连接超时: connection-timeout: 30000
   - 启用连接测试: connection-test-query: SELECT 1

3. **Redis缓存优化**
   - 增加最大内存: maxmemory 6gb
   - 优化淘汰策略: maxmemory-policy allkeys-lru
   - 启用内存碎片整理: activedefrag yes

### ⚡ 短期优化 (1-2周内)
1. **应用层优化**
   - 实现多级缓存策略
   - 优化推荐算法复杂度
   - 增加异步处理

2. **数据库优化**
   - 添加必要索引
   - 优化慢查询
   - 实现读写分离

3. **系统架构优化**
   - 实现负载均衡
   - 增加服务实例
   - 优化网络配置

### 🔧 中期优化 (1-2月内)
1. **微服务拆分**
   - 拆分推荐服务
   - 实现服务治理
   - 增加熔断机制

2. **基础设施优化**
   - 容器化部署
   - 自动扩缩容
   - 监控告警完善

## 预期效果
- QPS提升: 50-100%
- 响应时间减少: 30-50%
- 系统稳定性提升: 显著改善
- 资源利用率优化: 20-30%

## 下一步行动
1. 立即应用紧急优化配置
2. 重新执行性能测试验证效果
3. 根据结果调整优化策略
4. 建立持续性能监控

EOF

    echo "✅ 优化建议生成完成"
}

# 清理和总结
cleanup_and_summary() {
    echo "🧹 清理临时文件..."
    
    # 压缩大文件
    if [ -f "$RESULTS_DIR/target_test_$TIMESTAMP.jtl" ]; then
        gzip "$RESULTS_DIR/target_test_$TIMESTAMP.jtl"
    fi
    
    if [ -f "$RESULTS_DIR/baseline_test_$TIMESTAMP.jtl" ]; then
        gzip "$RESULTS_DIR/baseline_test_$TIMESTAMP.jtl"
    fi
    
    # 生成测试总结
    echo "📋 生成测试总结..."
    
    cat > "$RESULTS_DIR/test_summary_$TIMESTAMP.txt" << EOF
智能内容推荐平台性能测试总结
=====================================

测试时间: $(date '+%Y-%m-%d %H:%M:%S')
测试目标: $BASE_URL

生成的文件:
- 基准测试结果: baseline_test_$TIMESTAMP.jtl.gz
- 目标测试结果: target_test_$TIMESTAMP.jtl.gz
- HTML报告: baseline-report-$TIMESTAMP/index.html
- HTML报告: target-report-$TIMESTAMP/index.html
- 瓶颈分析报告: bottleneck_analysis_report.json
- 性能验证报告: performance_validation_report_*.json
- 优化建议: optimization_summary_$TIMESTAMP.md

查看方式:
1. 打开HTML报告查看详细测试结果
2. 查看JSON报告了解系统瓶颈
3. 阅读优化建议制定改进计划

下一步:
1. 应用优化建议
2. 重新执行测试
3. 对比优化前后效果
EOF

    echo "✅ 清理和总结完成"
}

# 主执行流程
main() {
    echo "开始完整性能测试流程..."
    
    # 1. 检查依赖
    check_dependencies
    
    # 2. 系统预热
    warm_up_system
    
    # 3. 执行基准测试
    run_baseline_test
    
    # 4. 执行目标性能测试
    run_target_performance_test
    
    # 5. 分析测试结果
    analyze_results
    
    # 6. 执行瓶颈分析
    run_bottleneck_analysis
    
    # 7. 验证性能目标
    validate_performance_targets
    
    # 8. 生成优化建议
    generate_optimization_recommendations
    
    # 9. 清理和总结
    cleanup_and_summary
    
    echo ""
    echo "=========================================="
    echo "✅ 完整性能测试流程执行完成!"
    echo "=========================================="
    echo "测试结果目录: $RESULTS_DIR"
    echo "主要报告文件:"
    echo "- HTML报告: $RESULTS_DIR/target-report-$TIMESTAMP/index.html"
    echo "- 优化建议: $RESULTS_DIR/optimization_summary_$TIMESTAMP.md"
    echo "- 测试总结: $RESULTS_DIR/test_summary_$TIMESTAMP.txt"
    echo ""
    echo "建议下一步操作:"
    echo "1. 查看HTML报告了解详细性能数据"
    echo "2. 阅读优化建议并应用相关配置"
    echo "3. 重新执行测试验证优化效果"
    echo "=========================================="
}

# 执行主流程
main "$@"