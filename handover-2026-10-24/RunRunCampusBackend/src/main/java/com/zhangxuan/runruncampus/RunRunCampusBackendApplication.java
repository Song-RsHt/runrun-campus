package com.zhangxuan.runruncampus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 旧系统后端应用启动组件；保持当前应用的配置与协作边界。 */
@SpringBootApplication
@EnableScheduling
public class RunRunCampusBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunRunCampusBackendApplication.class, args);
    }
}
