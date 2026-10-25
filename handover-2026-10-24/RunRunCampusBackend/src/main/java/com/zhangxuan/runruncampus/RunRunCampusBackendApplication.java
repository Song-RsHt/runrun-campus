package com.zhangxuan.runruncampus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RunRunCampusBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(RunRunCampusBackendApplication.class, args);
    }
}
