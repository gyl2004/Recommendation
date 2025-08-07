package com.recommendation.content.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger配置
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI contentServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("内容服务API")
                        .description("智能内容推荐平台 - 内容管理服务")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("推荐系统团队")
                                .email("team@recommendation.com")));
    }
}