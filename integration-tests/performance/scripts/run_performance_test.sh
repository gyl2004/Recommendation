#!/bin/bash

# 智能内容推荐平台性能测试执行脚本

set -e

# 配置参数
BASE_URL=${BASE_URL:-"http://localhost:8080"}
USERS=${USERS:-1000}
RAMP_TIME=${RAMP_TIME:-60}
DURATION=${DURATION:-300}
JMETER_HOME=${JMETER_HOME:-"/opt/apache-jmeter"}

# 创建结果目录
RESULTS_DIR="$(dirname "$0")/../results"
mkdir -p "$RESULTS_DIR"

# 清理旧的结果文件
rm -f "$RESULTS_DIR"/*.jtl
rm -f "$RESULTS_DIR"/*.log
rm -rf "$RESULTS_DIR"/html-report

echo "=========================================="
echo "智能内容推荐平台性能测试"
echo "=========================================="
echo "测试目标: $BASE_URL"
echo "并发用户数: $USERS"
echo "启动时间: $RAMP_TIME 秒"
echo "测试持续时间: $DURATION 秒"
echo "=========================================="

# 检查JMeter是否安装
if ! command -v jmeter &> /dev/null; then
    echo "错误: JMeter未安装或未在PATH中"
    echo "请安装JMeter并设置JMETER_HOME环境变量"
    exit 1
fi

# 检查服务是否可用
echo "检查服务可用性..."
if ! curl -f -s "$BASE_URL/actuator/health" > /dev/null; then
    echo "警告: 服务可能未启动，请确保所有服务正在运行"
    echo "可以使用以下命令启动服务:"
    echo "docker-compose up -d"
fi

# 执行性能测试
echo "开始执行性能测试..."
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

jmeter -n \
    -t "$(dirname "$0")/../jmeter/recommendation_load_test.jmx" \
    -l "$RESULTS_DIR/load_test_results_$TIMESTAMP.jtl" \
    -e \
    -o "$RESULTS_DIR/html-report-$TIMESTAMP" \
    -Jbase_url="$BASE_URL" \
    -Jusers="$USERS" \
    -Jramp_time="$RAMP_TIME" \
    -Jduration="$DURATION" \
    -Jjmeter.save.saveservice.output_format=xml \
    -Jjmeter.save.saveservice.response_data=false \
    -Jjmeter.save.saveservice.samplerData=false \
    -Jjmeter.save.saveservice.requestHeaders=false \
    -Jjmeter.save.saveservice.responseHeaders=false

echo "=========================================="
echo "性能测试完成!"
echo "=========================================="
echo "结果文件: $RESULTS_DIR/load_test_results_$TIMESTAMP.jtl"
echo "HTML报告: $RESULTS_DIR/html-report-$TIMESTAMP/index.html"
echo "=========================================="

# 分析测试结果
echo "分析测试结果..."
python3 "$(dirname "$0")/analyze_results.py" "$RESULTS_DIR/load_test_results_$TIMESTAMP.jtl"

# 生成性能报告
echo "生成性能报告..."
"$(dirname "$0")/generate_report.sh" "$RESULTS_DIR/load_test_results_$TIMESTAMP.jtl" "$RESULTS_DIR/html-report-$TIMESTAMP"

echo "测试完成! 请查看生成的报告文件。"