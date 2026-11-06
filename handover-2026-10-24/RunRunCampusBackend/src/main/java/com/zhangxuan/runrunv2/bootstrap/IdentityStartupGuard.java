package com.zhangxuan.runrunv2.bootstrap;

import com.zhangxuan.runrunv2.mapper.IdentityStartupMapper;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/** 新系统身份与会话启动校验组件；保持当前应用的配置与协作边界。 */
@Component
public class IdentityStartupGuard implements ApplicationRunner, InitializingBean {

    private final IdentityStartupMapper mapper;
    private final String environment;

    public IdentityStartupGuard(
            IdentityStartupMapper mapper,
            @Value("${rrc.identity.environment}") String environment) {
        this.mapper = mapper;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        run(null);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!Set.of("local", "test", "staging").contains(environment)) {
            throw new IllegalStateException(
                    "Stage one identity requires an isolated local/test/staging environment");
        }
        // 即使旧数据库旁有空 v2 schema，也必须拒绝连接旧库。
        Boolean legacy = mapper.isIdentitySchemaInstalled();
        if (Boolean.TRUE.equals(legacy)) {
            throw new IllegalStateException(
                    "Legacy public objects detected; an isolated v2 database is required");
        }
        mapper.countUsersWithControlledIdentityFlag();
        mapper.countIdentityTickets();
    }
}
