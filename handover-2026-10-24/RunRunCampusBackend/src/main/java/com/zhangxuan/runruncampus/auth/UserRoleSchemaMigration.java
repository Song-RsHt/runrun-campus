package com.zhangxuan.runruncampus.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class UserRoleSchemaMigration implements ApplicationRunner {
    static final String CONSTRAINT_QUERY = """
            SELECT pg_get_constraintdef(oid)
            FROM pg_constraint
            WHERE conrelid = 'app_users'::regclass
              AND conname = 'app_users_role_check'
            """;
    static final String DROP_CONSTRAINT =
            "ALTER TABLE app_users DROP CONSTRAINT IF EXISTS app_users_role_check";
    static final String ADD_CONSTRAINT =
            "ALTER TABLE app_users ADD CONSTRAINT app_users_role_check " +
                    "CHECK (role IN ('CUSTOMER', 'COURIER', 'ADMIN'))";

    private final JdbcTemplate jdbcTemplate;

    public UserRoleSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String definition;
        try {
            definition = jdbcTemplate.queryForObject(CONSTRAINT_QUERY, String.class);
        } catch (EmptyResultDataAccessException ignored) {
            definition = null;
        }

        if (definition != null && definition.contains("ADMIN")) return;

        jdbcTemplate.execute(DROP_CONSTRAINT);
        jdbcTemplate.execute(ADD_CONSTRAINT);
    }
}
