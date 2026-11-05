package com.zhangxuan.runrunv2.bootstrap;

import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdentityStartupGuard implements ApplicationRunner, org.springframework.beans.factory.InitializingBean {
    private final JdbcTemplate db;
    private final String environment;
    public IdentityStartupGuard(JdbcTemplate db, @Value("${rrc.identity.environment}") String environment) { this.db = db; this.environment = environment; }
    @Override public void afterPropertiesSet() { run(null); }
    @Override public void run(ApplicationArguments args) {
        if (!Set.of("local", "test", "staging").contains(environment)) throw new IllegalStateException("Stage one identity requires an isolated local/test/staging environment");
        // Refuse a legacy database even if someone placed an empty v2 schema alongside it.
        Boolean legacy = db.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f'))", Boolean.class);
        if (Boolean.TRUE.equals(legacy)) throw new IllegalStateException("Legacy public objects detected; an isolated v2 database is required");
        db.queryForObject("SELECT count(controlled_identity) FROM runrun_v2.app_users", Long.class);
        db.queryForObject("SELECT count(*) FROM runrun_v2.identity_tickets", Long.class);
    }
}
