package com.recommendation.datacollection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 数据收集服务启动类
 */
@SpringBootApplication(scanBasePackages = {
    "com.recommendation.datacollection",
    "com.recommendation.common"
})
public class DataCollectionServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DataCollectionServiceApplication.class, args);
    }
}