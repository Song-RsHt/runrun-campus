package com.zhangxuan.runruncampus.bootstrap;

import com.zhangxuan.runruncampus.mapper.UserRoleSchemaMigrationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;














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
            UserRoleSchemaMigrationMapper
                .CONSTRAINT_QUERY,
            String.class))
        .thenReturn(
            "CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'COURIER'::character"
                + " varying])::text[])))");
    migration.run(null);
    verify(jdbcTemplate)
        .execute(
            UserRoleSchemaMigrationMapper
                .DROP_CONSTRAINT);
    verify(jdbcTemplate)
        .execute(
            UserRoleSchemaMigrationMapper
                .ADD_CONSTRAINT);
  }

  @Test
  void leavesCurrentRoleConstraintUnchanged() {
    when(jdbcTemplate.queryForObject(
            UserRoleSchemaMigrationMapper
                .CONSTRAINT_QUERY,
            String.class))
        .thenReturn(
            "CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'COURIER'::character"
                + " varying, 'ADMIN'::character varying])::text[])))");
    migration.run(null);
    verify(jdbcTemplate, never())
        .execute(
            UserRoleSchemaMigrationMapper
                .DROP_CONSTRAINT);
    verify(jdbcTemplate, never())
        .execute(
            UserRoleSchemaMigrationMapper
                .ADD_CONSTRAINT);
  }
}
