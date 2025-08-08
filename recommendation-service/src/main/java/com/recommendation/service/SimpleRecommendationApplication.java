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
