package com.recommendation.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 用户服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.recommendation.user")
@EntityScan(basePackages = "com.recommendation.user.entity")
@EnableJpaRepositories(basePackages = "com.recommendation.user.repository")
public class UserServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}