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
