package com.zhangxuan.runrunv2.mapper;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL for LocalTestBootstrap. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class LocalTestMapper {

    private final JdbcTemplate jdbc;

    public LocalTestMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void initializeIsolatedFixtures() {
        jdbc.execute("""
            SELECT pg_advisory_xact_lock(7082001)
            """);
    }

    public List<Map<String, Object>> findLocalTestAdmin() {
        return jdbc.queryForList("""
            SELECT password_hash,
                role,
                v2_admin_enabled
            FROM runrun_v2.app_users
            WHERE phone = 'test-admin'
            """);
    }

    public Boolean hasLocalTestEnableAudit() {
        return jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM runrun_v2.admin_audit_logs
                WHERE request_id = 'local-test-enable'
                    AND reason = 'Isolated synthetic local test fixture')
            """, Boolean.class);
    }

    public Long countUsers() {
        return jdbc.queryForObject("""
            SELECT count(*)
            FROM runrun_v2.app_users
            """, Long.class);
    }

    public Long insertLocalTestAdmin(String passwordHash) {
        return jdbc.queryForObject("""
            INSERT INTO runrun_v2.app_users (
                phone,
                role,
                password_hash
            )
            VALUES (
                'test-admin',
                'USER',
                ?
            )
            RETURNING id
            """, Long.class, passwordHash);
    }

    public int insertWechatIdentity(long userId, String appId, String openidHash, String openidCiphertext) {
        return jdbc.update("""
            INSERT INTO runrun_v2.wechat_identities (
                user_id,
                app_id,
                openid_hash,
                openid_ciphertext
            )
            VALUES (
                ?,
                ?,
                ?,
                ?
            )
            """, userId, appId, openidHash, openidCiphertext);
    }
}
