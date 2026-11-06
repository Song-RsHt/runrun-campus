package com.zhangxuan.runruncampus.bootstrap;

import com.zhangxuan.runruncampus.mapper.UserRoleSchemaMigrationMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

















@Component
@Order(0)
public class UserRoleSchemaMigration implements ApplicationRunner {

  private final UserRoleSchemaMigrationMapper mapper;

  public UserRoleSchemaMigration(UserRoleSchemaMigrationMapper mapperTemplate) {
    this.mapper = mapperTemplate;
  }

  @Override
  public void run(ApplicationArguments args) {
    String definition;
    try {
      definition = mapper.findRoleConstraint();
    } catch (EmptyResultDataAccessException ignored) {
      definition = null;
    }
    if (definition != null && definition.contains("ADMIN")) return;
    mapper.dropRoleConstraint();
    mapper.addAdminRoleConstraint();
  }
}
