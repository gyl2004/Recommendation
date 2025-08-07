# 智能内容推荐平台集成测试

## 概述

本模块包含智能内容推荐平台的端到端集成测试，验证整个推荐流程的正确性、性能和可靠性。

## 测试架构

```
integration-tests/
├── src/test/java/                    # Java集成测试
│   └── com/recommendation/integration/
│       ├── BaseIntegrationTest.java          # 测试基类
│       ├── RecommendationE2ETest.java        # 端到端测试
│       ├── RecommendationAccuracyTest.java   # 准确性测试
│       └── EdgeCaseTest.java                 # 边界条件测试
├── python/                           # Python服务测试
│   ├── test_feature_service_integration.py  # 特征服务测试
│   └── test_ranking_service_integration.py  # 排序服务测试
├── scripts/                          # 测试脚本
│   ├── run-integration-tests.sh             # Linux/Mac运行脚本
│   └── run-integration-tests.bat            # Windows运行脚本
└── test-results/                     # 测试结果输出
```

## 测试覆盖范围

### 1. 端到端流程测试 (RecommendationE2ETest)

- **新用户冷启动推荐**: 测试新用户首次访问时的推荐策略
- **用户行为收集**: 验证用户行为数据的收集和处理
- **个性化推荐**: 测试基于用户画像的个性化推荐
- **推荐性能**: 验证推荐响应时间 < 500ms
- **缓存机制**: 测试多级缓存的命中率和性能
- **异常处理**: 验证各种异常情况的处理
- **服务降级**: 测试服务不可用时的降级策略
- **A/B测试**: 验证A/B测试框架的功能

### 2. 推荐准确性测试 (RecommendationAccuracyTest)

- **基于内容的推荐**: 验证内容相似度推荐的准确性
- **协同过滤推荐**: 测试用户相似性推荐的效果
- **推荐多样性**: 验证推荐结果的多样性指标
- **推荐新颖性**: 测试推荐内容的新颖性
- **时效性**: 验证推荐内容的时效性
- **个性化程度**: 测试不同用户推荐结果的差异化

### 3. 边界条件测试 (EdgeCaseTest)

- **空数据库推荐**: 测试无内容时的推荐行为
- **新用户无历史**: 验证新用户的推荐策略
- **极端参数**: 测试各种边界参数值
- **并发请求**: 验证高并发下的系统稳定性
- **数据一致性**: 测试数据更新的一致性
- **缓存失效**: 验证缓存失效机制
- **内存泄漏**: 测试长时间运行的内存使用
- **网络异常**: 测试网络故障恢复能力
- **数据格式异常**: 验证异常数据的处理
- **资源限制**: 测试系统资源限制

### 4. 特征服务测试 (test_feature_service_integration.py)

- **用户特征提取**: 测试用户行为特征的提取和存储
- **内容特征提取**: 验证内容特征的提取算法
- **实时特征更新**: 测试特征的实时更新机制
- **批量特征处理**: 验证批量特征处理的性能
- **特征质量验证**: 测试特征数据的质量控制
- **特征缓存性能**: 验证特征缓存的性能优化
- **并发特征请求**: 测试并发场景下的特征服务

### 5. 排序服务测试 (test_ranking_service_integration.py)

- **基础排序功能**: 测试排序算法的基本功能
- **多算法支持**: 验证不同排序算法的效果
- **多样性控制**: 测试推荐结果多样性的控制
- **排序性能**: 验证大量候选内容的排序性能
- **批量排序**: 测试批量排序请求的处理
- **模型A/B测试**: 验证不同模型版本的A/B测试
- **上下文感知**: 测试上下文信息对排序的影响

## 运行测试

### 前置条件

1. **Docker和Docker Compose**: 用于启动测试环境
2. **Java 11+**: 运行Java服务和测试
3. **Maven 3.6+**: 构建和运行Java测试
4. **Python 3.8+**: 运行Python服务和测试
5. **pip**: 安装Python依赖

### 快速开始

#### Linux/Mac

```bash
# 运行完整集成测试
./integration-tests/scripts/run-integration-tests.sh

# 只运行Java测试
./integration-tests/scripts/run-integration-tests.sh --java-only

# 只运行Python测试
./integration-tests/scripts/run-integration-tests.sh --python-only

# 保持服务运行（用于调试）
./integration-tests/scripts/run-integration-tests.sh --keep-services
```

#### Windows

```cmd
# 运行完整集成测试
integration-tests\scripts\run-integration-tests.bat
```

### 手动运行

#### 1. 启动基础设施

```bash
cd project-root
docker-compose up -d mysql redis elasticsearch rabbitmq
```

#### 2. 构建和启动应用服务

```bash
mvn clean package -DskipTests
docker-compose up -d recommendation-service user-service content-service feature-service ranking-service
```

#### 3. 运行Java测试

```bash
cd integration-tests
mvn test -Dspring.profiles.active=test
```

#### 4. 运行Python测试

```bash
cd integration-tests/python
pip install -r requirements.txt
python -m pytest -v
```

## 测试配置

### 环境配置

测试使用TestContainers自动管理测试环境，包括：

- **MySQL 8.0**: 业务数据存储
- **Redis 7**: 缓存和会话存储
- **Elasticsearch 7.17**: 内容搜索和索引
- **RabbitMQ 3.11**: 消息队列

### 性能基准

- **推荐响应时间**: < 500ms (95th percentile)
- **并发处理能力**: 10,000 QPS
- **缓存命中率**: > 80%
- **系统可用性**: > 99.9%
- **推荐准确率**: > 70%

### 测试数据

测试使用模拟数据，包括：

- **用户数据**: 不同年龄、兴趣的用户画像
- **内容数据**: 文章、视频、商品等多种类型内容
- **行为数据**: 浏览、点击、点赞、分享等用户行为
- **上下文数据**: 时间、地点、设备等上下文信息

## 测试报告

测试完成后会生成以下报告：

- **Java测试报告**: `test-results/java/surefire-reports/index.html`
- **Python测试报告**: `test-results/python/report.html`
- **代码覆盖率报告**: `test-results/python/coverage/index.html`
- **综合测试报告**: `test-results/integration-test-report.html`

## 故障排查

### 常见问题

1. **服务启动失败**
   - 检查端口是否被占用
   - 确认Docker服务正常运行
   - 查看服务日志：`docker-compose logs [service-name]`

2. **测试超时**
   - 增加测试超时时间
   - 检查网络连接
   - 确认服务健康状态

3. **数据库连接失败**
   - 确认MySQL容器正常运行
   - 检查数据库连接配置
   - 验证数据库初始化脚本

4. **缓存问题**
   - 清理Redis缓存：`docker exec redis redis-cli FLUSHALL`
   - 检查Redis连接配置
   - 验证缓存键的格式

### 调试技巧

1. **查看服务日志**
   ```bash
   docker-compose logs -f [service-name]
   ```

2. **进入容器调试**
   ```bash
   docker exec -it [container-name] /bin/bash
   ```

3. **检查服务健康状态**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

4. **监控系统资源**
   ```bash
   docker stats
   ```

## 持续集成

集成测试可以集成到CI/CD流水线中：

```yaml
# GitHub Actions示例
name: Integration Tests
on: [push, pull_request]
jobs:
  integration-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Set up JDK 11
        uses: actions/setup-java@v2
        with:
          java-version: '11'
      - name: Set up Python
        uses: actions/setup-python@v2
        with:
          python-version: '3.8'
      - name: Run Integration Tests
        run: ./integration-tests/scripts/run-integration-tests.sh
      - name: Upload Test Results
        uses: actions/upload-artifact@v2
        with:
          name: test-results
          path: integration-tests/test-results/
```

## 贡献指南

### 添加新测试

1. **Java测试**: 在`src/test/java`目录下创建测试类
2. **Python测试**: 在`python`目录下创建`test_*.py`文件
3. **遵循命名规范**: 使用描述性的测试方法名
4. **添加测试文档**: 在测试类中添加详细注释

### 测试最佳实践

1. **独立性**: 每个测试应该独立运行
2. **可重复性**: 测试结果应该一致
3. **清理**: 测试后清理测试数据
4. **断言**: 使用明确的断言验证结果
5. **性能**: 避免不必要的等待时间

## 许可证

本项目采用MIT许可证，详见LICENSE文件。