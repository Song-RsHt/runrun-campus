package com.zhangxuan.runruncampus.auth;

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
    @Mock
    private JdbcTemplate jdbcTemplate;

    private UserRoleSchemaMigration migration;

    @BeforeEach
    void setUp() {
        migration = new UserRoleSchemaMigration(jdbcTemplate);
    }

    @Test
    void expandsOldRoleConstraintToIncludeAdmin() {
        when(jdbcTemplate.queryForObject(UserRoleSchemaMigration.CONSTRAINT_QUERY, String.class))
                .thenReturn("CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'COURIER'::character varying])::text[])))");

        migration.run(null);

        verify(jdbcTemplate).execute(UserRoleSchemaMigration.DROP_CONSTRAINT);
        verify(jdbcTemplate).execute(UserRoleSchemaMigration.ADD_CONSTRAINT);
    }

    @Test
    void leavesCurrentRoleConstraintUnchanged() {
        when(jdbcTemplate.queryForObject(UserRoleSchemaMigration.CONSTRAINT_QUERY, String.class))
                .thenReturn("CHECK (((role)::text = ANY ((ARRAY['CUSTOMER'::character varying, 'COURIER'::character varying, 'ADMIN'::character varying])::text[])))");

        migration.run(null);

        verify(jdbcTemplate, never()).execute(UserRoleSchemaMigration.DROP_CONSTRAINT);
        verify(jdbcTemplate, never()).execute(UserRoleSchemaMigration.ADD_CONSTRAINT);
    }
}
