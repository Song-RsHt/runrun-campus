package com.zhangxuan.runruncampus.bootstrap;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhangxuan.runruncampus.mapper.identity.UserRoleSchemaMigrationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class UserRoleSchemaMigrationTest {

  @Mock private JdbcTemplate jdbcTemplate;

  private UserRoleSchemaMigration migration;

  @BeforeEach
  void setUp() {
    migration = new UserRoleSchemaMigration(new UserRoleSchemaMigrationMapper(jdbcTemplate));
  }

  @Test
  void expandsOldRoleConstraintToIncludeAdmin() {
    when(jdbcTemplate.queryForObject(
            com.zhangxuan.runruncampus.mapper.identity.UserRoleSchemaMigrationMapper
                .CONSTRAINT_QUERY,
            String.class))
        .thenReturn(
            "CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'COURIER'::character"
                + " varying])::text[])))");
    migration.run(null);
    verify(jdbcTemplate)
        .execute(
            com.zhangxuan.runruncampus.mapper.identity.UserRoleSchemaMigrationMapper
                .DROP_CONSTRAINT);
    verify(jdbcTemplate)
        .execute(
            com.zhangxuan.runruncampus.mapper.identity.UserRoleSchemaMigrationMapper
                .ADD_CONSTRAINT);
  }

  @Test
  void leavesCurrentRoleConstraintUnchanged() {
    when(jdbcTemplate.queryForObject(
            com.zhangxuan.runruncampus.mapper.identity.UserRoleSchemaMigrationMapper
                .CONSTRAINT_QUERY,
            String.class))
        .thenReturn(
            "CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'COURIER'::character"
                + " varying, 'ADMIN'::character varying])::text[])))");
    migration.run(null);
    verify(jdbcTemplate, never())
        .execute(
            com.zhangxuan.runruncampus.mapper.identity.UserRoleSchemaMigrationMapper
                .DROP_CONSTRAINT);
    verify(jdbcTemplate, never())
        .execute(
            com.zhangxuan.runruncampus.mapper.identity.UserRoleSchemaMigrationMapper
                .ADD_CONSTRAINT);
  }
}
