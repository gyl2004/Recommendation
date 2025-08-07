# 推荐系统监控指南

## 概述

本监控系统基于Prometheus + Grafana + AlertManager构建，为智能内容推荐平台提供全面的性能监控和告警功能。

## 架构组件

### 1. Prometheus (端口: 9090)
- **功能**: 指标收集和存储
- **配置文件**: `prometheus.yml`
- **告警规则**: `recommendation_rules.yml`
- **访问地址**: http://localhost:9090

### 2. Grafana (端口: 3000)
- **功能**: 数据可视化和仪表板
- **默认账号**: admin/admin123
- **访问地址**: http://localhost:3000

### 3. AlertManager (端口: 9093)
- **功能**: 告警管理和通知
- **配置文件**: `alertmanager.yml`
- **访问地址**: http://localhost:9093

## 监控指标

### 业务指标

#### 推荐服务指标
- `recommendation_request_total`: 推荐请求总数
- `recommendation_request_duration_seconds`: 推荐请求响应时间
- `recommendation_request_errors_total`: 推荐请求错误数
- `recommendation_qps`: 推荐服务QPS

#### 缓存指标
- `recommendation_cache_hits_total`: 缓存命中次数
- `recommendation_cache_misses_total`: 缓存未命中次数

#### 算法指标
- `recommendation_recall_duration_seconds`: 召回服务响应时间
- `recommendation_ranking_duration_seconds`: 排序服务响应时间
- `recommendation_algorithm_duration_seconds`: 算法处理时间
- `recommendation_algorithm_candidate_count`: 候选集大小
- `recommendation_algorithm_result_count`: 结果集大小

### 系统指标

#### 资源使用率
- `recommendation_system_cpu_usage`: CPU使用率
- `recommendation_system_memory_usage`: 内存使用率
- `recommendation_system_heap_usage`: JVM堆内存使用率

#### 连接池指标
- `recommendation_db_pool_active`: 数据库活跃连接数
- `recommendation_db_pool_idle`: 数据库空闲连接数
- `recommendation_db_pool_total`: 数据库总连接数
- `recommendation_redis_pool_active`: Redis活跃连接数
- `recommendation_redis_pool_idle`: Redis空闲连接数
- `recommendation_redis_pool_total`: Redis总连接数

#### 数据库性能
- `recommendation_database_operation_duration_seconds`: 数据库操作响应时间
- `recommendation_database_operation_total`: 数据库操作总数

#### Redis性能
- `recommendation_redis_operation_duration_seconds`: Redis操作响应时间
- `recommendation_redis_operation_total`: Redis操作总数

## 告警规则

### 关键告警

1. **高响应时间告警**
   - 条件: 95%分位响应时间 > 500ms
   - 持续时间: 2分钟
   - 级别: Warning

2. **高错误率告警**
   - 条件: 错误率 > 5%
   - 持续时间: 2分钟
   - 级别: Critical

3. **低缓存命中率告警**
   - 条件: 缓存命中率 < 80%
   - 持续时间: 5分钟
   - 级别: Warning

4. **高QPS告警**
   - 条件: QPS > 8000
   - 持续时间: 1分钟
   - 级别: Warning

5. **系统资源告警**
   - CPU使用率 > 80% (5分钟)
   - 内存使用率 > 85% (5分钟)
   - JVM堆内存使用率 > 90% (3分钟)

## 部署和启动

### 1. 使用Docker Compose启动

```bash
# 启动所有监控服务
docker-compose -f monitoring-docker-compose.yml up -d

# 查看服务状态
docker-compose -f monitoring-docker-compose.yml ps

# 查看日志
docker-compose -f monitoring-docker-compose.yml logs -f prometheus
```

### 2. 使用启动脚本

```bash
# 给脚本执行权限
chmod +x start-monitoring.sh

# 启动监控系统
./start-monitoring.sh
```

### 3. 验证部署

访问以下地址验证服务是否正常启动：

- Prometheus: http://localhost:9090/targets
- Grafana: http://localhost:3000
- AlertManager: http://localhost:9093

## 使用指南

### 1. Grafana仪表板

#### 访问仪表板
1. 打开 http://localhost:3000
2. 使用 admin/admin123 登录
3. 导航到 Dashboards > 推荐系统监控仪表板

#### 主要面板
- **推荐请求QPS**: 实时QPS监控
- **推荐请求响应时间**: P50/P95/P99响应时间
- **错误率**: 请求错误率统计
- **缓存命中率**: 缓存性能监控
- **系统资源使用率**: CPU/内存/堆内存使用情况
- **连接池状态**: 数据库和Redis连接池监控
- **算法性能指标**: 召回和排序服务性能

### 2. Prometheus查询

#### 常用查询语句

```promql
# 推荐服务QPS
rate(recommendation_request_total[1m])

# 95%分位响应时间
histogram_quantile(0.95, rate(recommendation_request_duration_seconds_bucket[5m]))

# 错误率
rate(recommendation_request_errors_total[5m]) / rate(recommendation_request_total[5m]) * 100

# 缓存命中率
rate(recommendation_cache_hits_total[5m]) / (rate(recommendation_cache_hits_total[5m]) + rate(recommendation_cache_misses_total[5m])) * 100

# 系统资源使用率
recommendation_system_cpu_usage
recommendation_system_memory_usage
recommendation_system_heap_usage
```

### 3. 告警配置

#### 邮件告警配置
编辑 `monitoring/alertmanager/alertmanager.yml`:

```yaml
global:
  smtp_smarthost: 'your-smtp-server:587'
  smtp_from: 'alerts@yourcompany.com'
  smtp_auth_username: 'your-username'
  smtp_auth_password: 'your-password'

receivers:
- name: 'critical-alerts'
  email_configs:
  - to: 'admin@yourcompany.com'
    subject: '[CRITICAL] 推荐系统告警'
```

#### Webhook告警配置
```yaml
receivers:
- name: 'webhook-alerts'
  webhook_configs:
  - url: 'http://your-webhook-endpoint'
    send_resolved: true
```

## API接口

### 监控API

#### 1. 健康检查
```bash
GET /api/v1/monitoring/health
```

#### 2. 指标摘要
```bash
GET /api/v1/monitoring/metrics/summary
```

#### 3. 记录自定义指标
```bash
POST /api/v1/monitoring/metrics/custom
Content-Type: application/json

{
  "metricName": "custom.metric.name",
  "value": 42.0,
  "tags": ["tag1", "value1", "tag2", "value2"]
}
```

#### 4. 触发系统资源收集
```bash
POST /api/v1/monitoring/collect/system
```

#### 5. 测试监控指标
```bash
POST /api/v1/monitoring/test/recommendation?count=100
```

### Actuator端点

#### 1. Prometheus指标
```bash
GET /actuator/prometheus
```

#### 2. 健康检查
```bash
GET /actuator/health
```

#### 3. 应用信息
```bash
GET /actuator/info
```

#### 4. 指标详情
```bash
GET /actuator/metrics
GET /actuator/metrics/{metric-name}
```

## 故障排查

### 1. 常见问题

#### Prometheus无法抓取指标
- 检查服务是否启动: `curl http://localhost:8080/actuator/prometheus`
- 检查防火墙设置
- 验证Prometheus配置文件语法

#### Grafana无法连接Prometheus
- 检查数据源配置: http://prometheus:9090
- 验证网络连通性
- 检查Prometheus服务状态

#### 告警不生效
- 检查告警规则语法
- 验证AlertManager配置
- 检查邮件服务器设置

### 2. 日志查看

```bash
# 查看Prometheus日志
docker logs prometheus

# 查看Grafana日志
docker logs grafana

# 查看AlertManager日志
docker logs alertmanager

# 查看推荐服务日志
docker logs recommendation-service
```

### 3. 性能调优

#### Prometheus存储优化
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

# 数据保留时间
command:
  - '--storage.tsdb.retention.time=30d'
  - '--storage.tsdb.retention.size=10GB'
```

#### Grafana性能优化
```ini
# grafana.ini
[database]
max_open_conn = 300
max_idle_conn = 300

[server]
enable_gzip = true
```

## 扩展和定制

### 1. 添加新的监控指标

```java
@Service
public class CustomMonitoringService {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    public void recordCustomBusinessMetric(String metricName, double value) {
        meterRegistry.gauge(metricName, value);
    }
}
```

### 2. 创建自定义仪表板

1. 在Grafana中创建新仪表板
2. 添加面板和查询
3. 导出JSON配置
4. 保存到 `monitoring/grafana/dashboards/` 目录

### 3. 添加新的告警规则

编辑 `recommendation_rules.yml`:

```yaml
- alert: CustomAlert
  expr: custom_metric > threshold
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "自定义告警"
    description: "自定义指标超过阈值"
```

## 最佳实践

1. **指标命名**: 使用一致的命名规范
2. **标签使用**: 合理使用标签进行分组和过滤
3. **告警设置**: 避免告警风暴，设置合理的阈值和持续时间
4. **数据保留**: 根据需求设置合适的数据保留策略
5. **性能监控**: 定期检查监控系统本身的性能
6. **文档维护**: 及时更新监控文档和运维手册

## 联系支持

如有问题，请联系：
- 技术支持: tech-support@recommendation.com
- 运维团队: ops@recommendation.com