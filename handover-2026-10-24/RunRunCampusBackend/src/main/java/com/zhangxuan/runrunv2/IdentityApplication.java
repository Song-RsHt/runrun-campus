package com.zhangxuan.runrunv2;

import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Separate scan/config prevents legacy controllers, repositories and migrations from starting. */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {FlywayAutoConfiguration.class, HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@ComponentScan(basePackageClasses = IdentityApplication.class)
@EnableScheduling
public class IdentityApplication {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer rejectUnknownJsonFields() {
        return builder -> builder.featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
    public static void main(String[] args) {
        System.setProperty("spring.config.name", "application-v2");
        SpringApplication.run(IdentityApplication.class, args);
    }
}
