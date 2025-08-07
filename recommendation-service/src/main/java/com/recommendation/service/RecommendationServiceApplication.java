package com.recommendation.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 推荐服务启动类
 */
@SpringBootApplication(scanBasePackages = {
    "com.recommendation.service",
    "com.recommendation.common"
})
@EntityScan(basePackages = "com.recommendation.common.domain")
@EnableJpaRepositories(basePackages = "com.recommendation.service.repository")
@EnableAsync
@EnableScheduling
public class RecommendationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(RecommendationServiceApplication.class, args);
    }
}