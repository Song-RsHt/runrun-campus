package com.zhangxuan.runrunv2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** Separate scan/config prevents legacy controllers, repositories and migrations from starting. */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {FlywayAutoConfiguration.class, HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@ComponentScan(basePackageClasses = IdentityApplication.class)
public class IdentityApplication {
    public static void main(String[] args) {
        System.setProperty("spring.config.name", "application-v2");
        SpringApplication.run(IdentityApplication.class, args);
    }
}
