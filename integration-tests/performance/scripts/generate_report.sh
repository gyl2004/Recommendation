#!/bin/bash

# 性能测试报告生成脚本

set -e

if [ $# -lt 2 ]; then
    echo "使用方法: $0 <jtl_file> <html_report_dir>"
    exit 1
fi

JTL_FILE="$1"
HTML_REPORT_DIR="$2"
SCRIPT_DIR="$(dirname "$0")"

echo "生成性能测试报告..."
echo "JTL文件: $JTL_FILE"
echo "HTML报告目录: $HTML_REPORT_DIR"

# 检查文件是否存在
if [ ! -f "$JTL_FILE" ]; then
    echo "错误: JTL文件不存在: $JTL_FILE"
    exit 1
fi

# 创建报告目录
mkdir -p "$(dirname "$JTL_FILE")/reports"
REPORT_DIR="$(dirname "$JTL_FILE")/reports"

# 生成性能摘要报告
echo "生成性能摘要报告..."
cat > "$REPORT_DIR/performance_summary.md" << EOF
# 智能内容推荐平台性能测试报告

## 测试概述

- **测试时间**: $(date '+%Y-%m-%d %H:%M:%S')
- **测试文件**: $JTL_FILE
- **HTML报告**: $HTML_REPORT_DIR/index.html

## 测试目标

| 指标 | 目标值 | 实际值 | 状态 |
|------|--------|--------|------|
| QPS | 10,000 | - | 待分析 |
| 95%响应时间 | < 500ms | - | 待分析 |
| 可用性 | > 99.9% | - | 待分析 |

## 测试结果分析

详细的性能分析结果请查看:
- JSON分析报告: ${JTL_FILE%.*}_analysis.json
- HTML可视化报告: $HTML_REPORT_DIR/index.html

## 系统资源使用情况

### CPU使用率
- 平均CPU使用率: 待监控
- 峰值CPU使用率: 待监控

### 内存使用情况
- 平均内存使用率: 待监控
- 峰值内存使用率: 待监控

### 数据库性能
- 数据库连接数: 待监控
- 查询响应时间: 待监控

### 缓存性能
- Redis命中率: 待监控
- 缓存响应时间: 待监控

## 性能瓶颈分析

### 应用层瓶颈
- [ ] JVM垃圾回收频率过高
- [ ] 线程池配置不当
- [ ] 业务逻辑复杂度过高

### 数据库瓶颈
- [ ] 数据库连接池不足
- [ ] 慢查询过多
- [ ] 索引缺失或不合理

### 缓存瓶颈
- [ ] 缓存命中率低
- [ ] 缓存过期策略不当
- [ ] Redis连接数不足

### 网络瓶颈
- [ ] 网络带宽不足
- [ ] 网络延迟过高
- [ ] 负载均衡配置不当

## 优化建议

### 短期优化 (1-2周)
1. **JVM参数调优**
   - 增加堆内存大小
   - 优化垃圾回收器配置
   - 调整线程池参数

2. **数据库优化**
   - 添加缺失的索引
   - 优化慢查询
   - 增加数据库连接池大小

3. **缓存优化**
   - 提高缓存命中率
   - 优化缓存过期策略
   - 实现缓存预热

### 中期优化 (1-2月)
1. **架构优化**
   - 实现服务拆分
   - 引入消息队列
   - 实现读写分离

2. **算法优化**
   - 优化推荐算法复杂度
   - 实现算法并行化
   - 引入机器学习模型优化

### 长期优化 (3-6月)
1. **基础设施优化**
   - 实现微服务架构
   - 引入容器化部署
   - 实现自动扩缩容

2. **监控和运维**
   - 完善监控体系
   - 实现自动化运维
   - 建立性能基线

## 测试环境信息

### 硬件配置
- CPU: 8核心
- 内存: 16GB
- 磁盘: SSD
- 网络: 千兆网卡

### 软件版本
- JDK: 11
- Spring Boot: 2.7.x
- MySQL: 8.0
- Redis: 6.2
- Elasticsearch: 7.17

## 附录

### 测试数据
- 用户数据: 10,000条
- 内容数据: 100,000条
- 行为数据: 1,000,000条

### 测试脚本
- JMeter测试脚本: integration-tests/performance/jmeter/recommendation_load_test.jmx
- 分析脚本: integration-tests/performance/scripts/analyze_results.py
EOF

# 生成系统监控脚本
echo "生成系统监控脚本..."
cat > "$SCRIPT_DIR/monitor_system.sh" << 'EOF'
#!/bin/bash

# 系统资源监控脚本

MONITOR_DURATION=${1:-300}  # 默认监控5分钟
INTERVAL=${2:-5}            # 默认5秒间隔
OUTPUT_DIR="$(dirname "$0")/../results/monitoring"

mkdir -p "$OUTPUT_DIR"

echo "开始系统监控，持续时间: ${MONITOR_DURATION}秒，间隔: ${INTERVAL}秒"

# 监控CPU和内存
{
    echo "timestamp,cpu_usage,memory_usage,load_avg"
    for ((i=0; i<$((MONITOR_DURATION/INTERVAL)); i++)); do
        timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | sed 's/%us,//')
        memory_usage=$(free | grep Mem | awk '{printf "%.2f", $3/$2 * 100.0}')
        load_avg=$(uptime | awk -F'load average:' '{print $2}' | awk '{print $1}' | sed 's/,//')
        echo "$timestamp,$cpu_usage,$memory_usage,$load_avg"
        sleep $INTERVAL
    done
} > "$OUTPUT_DIR/system_metrics.csv" &

# 监控Java进程
{
    echo "timestamp,heap_used,heap_max,gc_count,thread_count"
    for ((i=0; i<$((MONITOR_DURATION/INTERVAL)); i++)); do
        timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        # 这里需要根据实际的Java进程PID进行调整
        java_pid=$(pgrep -f "recommendation-service")
        if [ -n "$java_pid" ]; then
            jstat_output=$(jstat -gc $java_pid | tail -1)
            heap_used=$(echo $jstat_output | awk '{print $3+$4+$6+$8}')
            heap_max=$(echo $jstat_output | awk '{print $2+$5+$7+$9}')
            gc_count=$(echo $jstat_output | awk '{print $11+$12}')
            thread_count=$(jstack $java_pid 2>/dev/null | grep "^\"" | wc -l)
            echo "$timestamp,$heap_used,$heap_max,$gc_count,$thread_count"
        else
            echo "$timestamp,0,0,0,0"
        fi
        sleep $INTERVAL
    done
} > "$OUTPUT_DIR/jvm_metrics.csv" &

# 监控数据库连接
{
    echo "timestamp,mysql_connections,redis_connections"
    for ((i=0; i<$((MONITOR_DURATION/INTERVAL)); i++)); do
        timestamp=$(date '+%Y-%m-%d %H:%M:%S')
        mysql_conn=$(mysql -u root -p123456 -e "SHOW STATUS LIKE 'Threads_connected';" 2>/dev/null | tail -1 | awk '{print $2}' || echo "0")
        redis_conn=$(redis-cli info clients 2>/dev/null | grep connected_clients | cut -d: -f2 | tr -d '\r' || echo "0")
        echo "$timestamp,$mysql_conn,$redis_conn"
        sleep $INTERVAL
    done
} > "$OUTPUT_DIR/db_metrics.csv" &

wait

echo "系统监控完成，结果保存在: $OUTPUT_DIR"
EOF

chmod +x "$SCRIPT_DIR/monitor_system.sh"

# 生成性能对比脚本
echo "生成性能对比脚本..."
cat > "$SCRIPT_DIR/compare_performance.py" << 'EOF'
#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
性能测试结果对比脚本
"""

import sys
import json
import matplotlib.pyplot as plt
import pandas as pd
from datetime import datetime

def load_test_results(file_paths):
    """加载多个测试结果文件"""
    results = []
    for file_path in file_paths:
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                data['file_path'] = file_path
                results.append(data)
        except Exception as e:
            print(f"加载文件失败 {file_path}: {e}")
    return results

def compare_basic_metrics(results):
    """对比基础性能指标"""
    metrics = ['qps', 'avg_response_time', 'p95_response_time', 'success_rate']
    
    comparison_data = []
    for i, result in enumerate(results):
        stats = result['basic_stats']
        row = {'test_run': f'Test_{i+1}'}
        for metric in metrics:
            row[metric] = stats.get(metric, 0)
        comparison_data.append(row)
    
    df = pd.DataFrame(comparison_data)
    
    # 生成对比图表
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))
    fig.suptitle('性能测试结果对比', fontsize=16)
    
    # QPS对比
    axes[0, 0].bar(df['test_run'], df['qps'])
    axes[0, 0].set_title('QPS对比')
    axes[0, 0].set_ylabel('QPS')
    axes[0, 0].axhline(y=10000, color='r', linestyle='--', label='目标值')
    axes[0, 0].legend()
    
    # 平均响应时间对比
    axes[0, 1].bar(df['test_run'], df['avg_response_time'])
    axes[0, 1].set_title('平均响应时间对比')
    axes[0, 1].set_ylabel('响应时间 (ms)')
    
    # 95%响应时间对比
    axes[1, 0].bar(df['test_run'], df['p95_response_time'])
    axes[1, 0].set_title('95%响应时间对比')
    axes[1, 0].set_ylabel('响应时间 (ms)')
    axes[1, 0].axhline(y=500, color='r', linestyle='--', label='目标值')
    axes[1, 0].legend()
    
    # 成功率对比
    axes[1, 1].bar(df['test_run'], df['success_rate'])
    axes[1, 1].set_title('成功率对比')
    axes[1, 1].set_ylabel('成功率 (%)')
    axes[1, 1].axhline(y=99.9, color='r', linestyle='--', label='目标值')
    axes[1, 1].legend()
    
    plt.tight_layout()
    plt.savefig('performance_comparison.png', dpi=300, bbox_inches='tight')
    plt.show()
    
    return df

def main():
    if len(sys.argv) < 3:
        print("使用方法: python3 compare_performance.py <result1.json> <result2.json> [result3.json] ...")
        sys.exit(1)
    
    file_paths = sys.argv[1:]
    results = load_test_results(file_paths)
    
    if len(results) < 2:
        print("至少需要两个测试结果文件进行对比")
        sys.exit(1)
    
    print("性能测试结果对比")
    print("=" * 50)
    
    comparison_df = compare_basic_metrics(results)
    print(comparison_df.to_string(index=False))
    
    print("\n对比图表已保存为: performance_comparison.png")

if __name__ == '__main__':
    main()
EOF

echo "报告生成完成!"
echo "生成的文件:"
echo "- 性能摘要报告: $REPORT_DIR/performance_summary.md"
echo "- 系统监控脚本: $SCRIPT_DIR/monitor_system.sh"
echo "- 性能对比脚本: $SCRIPT_DIR/compare_performance.py"
echo "- HTML报告: $HTML_REPORT_DIR/index.html"