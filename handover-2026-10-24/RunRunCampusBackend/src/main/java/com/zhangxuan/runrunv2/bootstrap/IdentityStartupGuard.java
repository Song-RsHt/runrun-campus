package com.zhangxuan.runrunv2.bootstrap;

import com.zhangxuan.runrunv2.mapper.identity.IdentityStartupMapper;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class IdentityStartupGuard
    implements ApplicationRunner, org.springframework.beans.factory.InitializingBean {
  private final IdentityStartupMapper mapper;
  private final String environment;

  public IdentityStartupGuard(
      IdentityStartupMapper mapper, @Value("${rrc.identity.environment}") String environment) {
    this.mapper = mapper;
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    run(null);
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!Set.of("local", "test", "staging").contains(environment))
      throw new IllegalStateException(
          "Stage one identity requires an isolated local/test/staging environment");
    // Refuse a legacy database even if someone placed an empty v2 schema alongside it.
    Boolean legacy = mapper.runValueStatement();
    if (Boolean.TRUE.equals(legacy))
      throw new IllegalStateException(
          "Legacy public objects detected; an isolated v2 database is required");
    mapper.runValueAppUsers();
    mapper.runValueIdentityTickets();
  }
}
