package com.zhangxuan.runruncampus.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** UserRoleSchemaMigration数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class UserRoleSchemaMigrationMapper {

    public static final String ADD_CONSTRAINT =
            """
            ALTER TABLE app_users ADD CONSTRAINT app_users_role_check CHECK (role IN ('CUSTOMER', 'COURIER', 'ADMIN'))
            """;

    public static final String DROP_CONSTRAINT =
            """
            ALTER TABLE app_users DROP CONSTRAINT IF EXISTS app_users_role_check
            """;

    public static final String CONSTRAINT_QUERY =
            """
            SELECT pg_get_constraintdef(oid)
            FROM pg_constraint
            WHERE conrelid = 'app_users'::regclass
              AND conname = 'app_users_role_check'
            """;

    private final JdbcTemplate jdbc;

    public UserRoleSchemaMigrationMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String findRoleConstraint() {
        return jdbc.queryForObject(CONSTRAINT_QUERY, String.class);
    }

    public void dropRoleConstraint() {
        jdbc.execute(DROP_CONSTRAINT);
    }

    public void addAdminRoleConstraint() {
        jdbc.execute(ADD_CONSTRAINT);
    }
}
