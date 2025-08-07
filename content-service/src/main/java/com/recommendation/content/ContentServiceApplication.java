package com.recommendation.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 内容服务启动类
 */
@SpringBootApplication
@EntityScan(basePackages = "com.recommendation.content.entity")
@EnableJpaRepositories(basePackages = "com.recommendation.content.repository")
public class ContentServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}