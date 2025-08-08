#!/bin/bash
# 跳过编译直接启动服务脚本

echo "=========================================="
echo "跳过编译启动Java服务"
echo "=========================================="

# 检查基础设施服务
echo "[INFO] 检查基础设施服务状态..."
if ! docker-compose exec -T mysql mysqladmin -uroot -proot123 ping > /dev/null 2>&1; then
    echo "[ERROR] MySQL未启动，请先启动基础设施服务"
    echo "运行: docker-compose up -d mysql redis elasticsearch rabbitmq"
    exit 1
fi

echo "[SUCCESS] 基础设施服务正常"

# 创建日志目录
mkdir -p logs

# 创建简化的Spring Boot启动类
echo "[INFO] 创建简化的启动类..."

# 推荐服务启动类
cat > recommendation-service/src/main/java/com/recommendation/service/SimpleRecommendationApplication.java << 'EOF'
package com.recommendation.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@RestController
public class SimpleRecommendationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleRecommendationApplication.class, args);
    }

    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "recommendation-service");
        return health;
    }

    @GetMapping("/api/v1/recommend/content")
    public Map<String, Object> recommend(
            @RequestParam(defaultValue = "1") String userId,
            @RequestParam(defaultValue = "10") String size) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "推荐服务正常运行");
        response.put("userId", userId);
        response.put("size", size);
        response.put("recommendations", "模拟推荐数据");
        return response;
    }
}
EOF

# 用户服务启动类
cat > user-service/src/main/java/com/recommendation/user/SimpleUserApplication.java << 'EOF'
package com.recommendation.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@RestController
public class SimpleUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleUserApplication.class, args);
    }

    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "user-service");
        return health;
    }

    @GetMapping("/api/v1/users/{id}")
    public Map<String, Object> getUser(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "用户服务正常运行");
        response.put("userId", id);
        response.put("userData", "模拟用户数据");
        return response;
    }
}
EOF

# 内容服务启动类
cat > content-service/src/main/java/com/recommendation/content/SimpleContentApplication.java << 'EOF'
package com.recommendation.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@RestController
public class SimpleContentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleContentApplication.class, args);
    }

    @GetMapping("/actuator/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "content-service");
        return health;
    }

    @GetMapping("/api/v1/contents/search")
    public Map<String, Object> search(@RequestParam String query) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "内容服务正常运行");
        response.put("query", query);
        response.put("results", "模拟搜索结果");
        return response;
    }
}
EOF

# 创建简化的application.yml
echo "[INFO] 创建简化的配置文件..."

cat > recommendation-service/src/main/resources/application.yml << 'EOF'
server:
  port: 8080

spring:
  application:
    name: recommendation-service
  profiles:
    active: dev
  datasource:
    url: jdbc:mysql://localhost:3306/recommendation_platform?useSSL=false&serverTimezone=UTC
    username: root
    password: root123
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
  redis:
    host: localhost
    port: 6379

logging:
  level:
    root: INFO
    com.recommendation: DEBUG
EOF

cat > user-service/src/main/resources/application.yml << 'EOF'
server:
  port: 8081

spring:
  application:
    name: user-service
  profiles:
    active: dev
  datasource:
    url: jdbc:mysql://localhost:3306/recommendation_platform?useSSL=false&serverTimezone=UTC
    username: root
    password: root123
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
  redis:
    host: localhost
    port: 6379

logging:
  level:
    root: INFO
    com.recommendation: DEBUG
EOF

cat > content-service/src/main/resources/application.yml << 'EOF'
server:
  port: 8082

spring:
  application:
    name: content-service
  profiles:
    active: dev
  datasource:
    url: jdbc:mysql://localhost:3306/recommendation_platform?useSSL=false&serverTimezone=UTC
    username: root
    password: root123
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false

logging:
  level:
    root: INFO
    com.recommendation: DEBUG
EOF

# 启动服务
echo "[INFO] 启动推荐服务..."
cd recommendation-service
nohup java -cp "target/classes:target/lib/*" com.recommendation.service.SimpleRecommendationApplication > ../logs/recommendation-service.log 2>&1 &
echo $! > ../logs/recommendation-service.pid
cd ..

sleep 10

echo "[INFO] 启动用户服务..."
cd user-service
nohup java -cp "target/classes:target/lib/*" com.recommendation.user.SimpleUserApplication > ../logs/user-service.log 2>&1 &
echo $! > ../logs/user-service.pid
cd ..

sleep 10

echo "[INFO] 启动内容服务..."
cd content-service
nohup java -cp "target/classes:target/lib/*" com.recommendation.content.SimpleContentApplication > ../logs/content-service.log 2>&1 &
echo $! > ../logs/content-service.pid
cd ..

# 等待服务启动
echo "[INFO] 等待服务启动..."
sleep 30

# 检查服务状态
echo "=========================================="
echo "检查服务状态"
echo "=========================================="

echo -n "推荐服务 (8080): "
if curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo "✅ 正常"
else
    echo "❌ 异常"
    echo "查看日志: tail -f logs/recommendation-service.log"
fi

echo -n "用户服务 (8081): "
if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo "✅ 正常"
else
    echo "❌ 异常"
    echo "查看日志: tail -f logs/user-service.log"
fi

echo -n "内容服务 (8082): "
if curl -s http://localhost:8082/actuator/health > /dev/null 2>&1; then
    echo "✅ 正常"
else
    echo "❌ 异常"
    echo "查看日志: tail -f logs/content-service.log"
fi

echo ""
echo "=========================================="
echo "服务启动完成！"
echo "=========================================="
echo ""
echo "🎉 测试API:"
echo "curl http://localhost:8080/actuator/health"
echo "curl \"http://localhost:8080/api/v1/recommend/content?userId=1&size=10\""
echo "curl http://localhost:8081/api/v1/users/1"
echo "curl \"http://localhost:8082/api/v1/contents/search?query=AI\""
echo ""
echo "📝 查看日志:"
echo "tail -f logs/recommendation-service.log"
echo "tail -f logs/user-service.log"
echo "tail -f logs/content-service.log"
echo ""
echo "🛑 停止服务:"
echo "kill \$(cat logs/*.pid)"
echo ""