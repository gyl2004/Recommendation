package com.recommendation.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.sql.DataSource;

/**
 * Flyway数据库迁移配置
 * 用于管理数据库版本和自动执行DDL脚本
 */
@Configuration
public class FlywayConfig {

    @Autowired
    private DataSource dataSource;

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String[] locations;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    @Value("${spring.flyway.validate-on-migrate:true}")
    private boolean validateOnMigrate;

    @Value("${spring.flyway.out-of-order:false}")
    private boolean outOfOrder;

    /**
     * 配置Flyway实例
     */
    @Bean(initMethod = "migrate")
    @DependsOn("dataSource")
    public Flyway flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(baselineOnMigrate)
                .validateOnMigrate(validateOnMigrate)
                .outOfOrder(outOfOrder)
                .encoding("UTF-8")
                .table("flyway_schema_history")
                .load();
    }
}