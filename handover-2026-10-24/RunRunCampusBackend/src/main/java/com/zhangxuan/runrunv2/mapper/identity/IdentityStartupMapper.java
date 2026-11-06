package com.zhangxuan.runrunv2.mapper.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** IdentityStartupGuard数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class IdentityStartupMapper {

    private final JdbcTemplate jdbc;

    public IdentityStartupMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Boolean isIdentitySchemaInstalled() {
        return jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_class c
                    JOIN pg_namespace n
                    ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public'
                        AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f'))
                """,
                Boolean.class);
    }

    public Long countUsersWithControlledIdentityFlag() {
        return jdbc.queryForObject(
                """
                SELECT count(controlled_identity)
                FROM runrun_v2.app_users
                """,
                Long.class);
    }

    public Long countIdentityTickets() {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.identity_tickets
                """,
                Long.class);
    }
}
