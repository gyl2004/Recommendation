# 智能内容推荐平台性能测试指南

## 概述

本指南详细说明如何执行智能内容推荐平台的性能测试，包括10000 QPS压力测试、500ms响应时间验证和99.9%可用性目标验证。

## 测试目标

| 指标 | 目标值 | 验证方法 |
|------|--------|----------|
| QPS | 10,000 | JMeter压力测试 |
| 95%响应时间 | < 500ms | 响应时间分析 |
| 系统可用性 | > 99.9% | 错误率统计 |
| 系统稳定性 | 长时间运行无崩溃 | 压力测试 |

## 环境准备

### 1. 硬件要求

**最低配置:**
- CPU: 8核心
- 内存: 16GB
- 磁盘: 100GB SSD
- 网络: 千兆网卡

**推荐配置:**
- CPU: 16核心
- 内存: 32GB
- 磁盘: 200GB NVMe SSD
- 网络: 万兆网卡

### 2. 软件依赖

**必需软件:**
- JDK 11+
- Apache JMeter 5.5+
- Python 3.8+
- MySQL 8.0
- Redis 6.2+
- Elasticsearch 7.17

**Python依赖包:**
```bash
pip install requests psutil mysql-connector-python redis matplotlib pandas
```

### 3. 服务启动

确保以下服务正在运行:

```bash
# 启动所有服务
docker-compose up -d

# 验证服务状态
curl http://localhost:8080/actuator/health
curl http://localhost:3306  # MySQL
curl http://localhost:6379  # Redis
curl http://localhost:9200  # Elasticsearch
```

## 测试执行

### 方式一: 完整自动化测试 (推荐)

**Windows:**
```cmd
cd integration-tests\performance
run_complete_performance_test.bat
```

**Linux/Mac:**
```bash
cd integration-tests/performance
chmod +x run_complete_performance_test.sh
./run_complete_performance_test.sh
```

### 方式二: 分步执行测试

#### 1. 基础性能测试
```bash
# 执行JMeter测试
cd integration-tests/performance/scripts
./run_performance_test.sh

# 分析结果
python3 analyze_results.py ../results/load_test_results_*.jtl
```

#### 2. 系统瓶颈分析
```bash
python3 bottleneck_analysis.py ../results/load_test_results_*.jtl
```

#### 3. 性能目标验证
```bash
python3 performance_validation.py http://localhost:8080
```

### 方式三: 自定义测试参数

```bash
# 自定义QPS和持续时间
BASE_URL=http://localhost:8080 \
USERS=2000 \
RAMP_TIME=120 \
DURATION=600 \
./run_performance_test.sh
```

## 测试结果分析

### 1. HTML报告

测试完成后会生成HTML报告，包含:
- 响应时间分布图
- QPS趋势图
- 错误率统计
- 资源使用情况

**查看方式:**
打开 `results/html-report-*/index.html`

### 2. JSON分析报告

详细的性能分析数据，包含:
- 基础性能指标
- 接口性能分析
- 错误模式分析
- 性能目标达成情况

**文件位置:**
`results/*_analysis.json`

### 3. 系统瓶颈报告

系统资源和瓶颈分析，包含:
- CPU、内存、磁盘使用情况
- JVM性能分析
- 数据库性能分析
- Redis缓存分析

**文件位置:**
`bottleneck_analysis_report.json`

## 性能优化

### 1. JVM优化

**推荐配置:**
```bash
-Xms4g -Xmx4g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:G1HeapRegionSize=16m
-XX:+PrintGCDetails
-Xloggc:logs/gc.log
```

**应用方式:**
参考 `optimization/jvm_optimization.md`

### 2. 数据库优化

**MySQL配置:**
```sql
-- 执行优化脚本
source optimization/database_optimization.sql;
```

**连接池配置:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
```

### 3. Redis优化

**配置文件:**
```bash
# 使用优化配置
cp optimization/redis_optimization.conf /etc/redis/redis.conf
systemctl restart redis
```

### 4. 应用配置优化

**Spring Boot配置:**
```bash
# 使用优化配置
cp optimization/application_optimization.yml src/main/resources/application-prod.yml
```

## 常见问题排查

### 1. QPS达不到目标

**可能原因:**
- JVM堆内存不足
- 数据库连接池过小
- 网络带宽限制
- 算法复杂度过高

**排查步骤:**
1. 检查JVM GC日志
2. 监控数据库连接数
3. 分析网络流量
4. 优化推荐算法

### 2. 响应时间过长

**可能原因:**
- 数据库查询慢
- 缓存命中率低
- 网络延迟高
- 业务逻辑复杂

**排查步骤:**
1. 分析慢查询日志
2. 检查缓存命中率
3. 测试网络延迟
4. 优化业务逻辑

### 3. 系统不稳定

**可能原因:**
- 内存泄漏
- 连接泄漏
- 线程死锁
- 资源竞争

**排查步骤:**
1. 生成堆转储分析
2. 检查连接池状态
3. 分析线程转储
4. 监控系统资源

## 持续监控

### 1. 建立性能基线

```bash
# 定期执行基准测试
crontab -e
0 2 * * 0 /path/to/run_performance_test.sh
```

### 2. 监控告警

**关键指标:**
- QPS < 8000 (告警)
- 95%响应时间 > 500ms (告警)
- 错误率 > 1% (告警)
- CPU使用率 > 80% (警告)
- 内存使用率 > 85% (警告)

### 3. 性能回归测试

**触发条件:**
- 代码发布前
- 配置变更后
- 基础设施升级后
- 定期回归测试

## 测试报告模板

### 性能测试报告

**测试概述:**
- 测试时间: [日期时间]
- 测试环境: [环境描述]
- 测试目标: [性能目标]

**测试结果:**
- 实际QPS: [数值]
- 平均响应时间: [数值]ms
- 95%响应时间: [数值]ms
- 系统可用性: [百分比]%

**目标达成情况:**
- [x] QPS目标达成
- [x] 响应时间目标达成
- [x] 可用性目标达成

**优化建议:**
1. [具体建议1]
2. [具体建议2]
3. [具体建议3]

**下一步计划:**
1. [行动计划1]
2. [行动计划2]
3. [行动计划3]

## 联系支持

如果在性能测试过程中遇到问题，请:

1. 查看测试日志文件
2. 检查系统资源使用情况
3. 参考常见问题排查步骤
4. 联系技术支持团队

**日志文件位置:**
- 应用日志: `logs/recommendation-service.log`
- GC日志: `logs/gc.log`
- 测试日志: `results/*.log`