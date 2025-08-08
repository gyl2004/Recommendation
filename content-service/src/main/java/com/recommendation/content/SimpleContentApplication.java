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
