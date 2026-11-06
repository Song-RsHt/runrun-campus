package com.zhangxuan.runrunv2.mapper;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL for DeploymentIdentityService. Uses the caller's transaction-bound JDBC connection; never
 * opens or commits a transaction.
 */
@Repository
public class DeploymentIdentityMapper {

    private final JdbcTemplate jdbc;

    public DeploymentIdentityMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Boolean deploymentIdentityExists() {
        return jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM runrun_v2.app_users
                WHERE controlled_identity)
            """, Boolean.class);
    }

    public Long insertDeploymentIdentity() {
        return jdbc.queryForObject("""
            INSERT INTO runrun_v2.app_users (
                phone,
                role,
                controlled_identity
            )
            VALUES (
                'deployment:' || gen_random_uuid(),
                'ADMIN',
                true
            )
            RETURNING id
            """, Long.class);
    }

    public Boolean hasWechatBinding(long userId) {
        return jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM runrun_v2.wechat_identities
                WHERE user_id = ?)
            """, Boolean.class, userId);
    }

    public int promoteToDisabledAdmin(long next, long userId) {
        return jdbc.update("""
            UPDATE runrun_v2.app_users
            SET role = 'ADMIN',
                v2_admin_enabled = false,
                v2_admin_security_version = ?,
                version = version + 1,
                updated_at = now()
            WHERE id = ?
            """, next, userId);
    }

    public Boolean settingsInstalled() {
        return jdbc.queryForObject("""
            SELECT to_regclass('runrun_v2.platform_settings') IS NOT NULL
            """, Boolean.class);
    }

    public Boolean isReviewOwner(long userId) {
        return jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM runrun_v2.platform_settings
                WHERE review_owner_id = ?)
            """, Boolean.class, userId);
    }

    public int updateAdminAccess(boolean enabled, long next, long userId) {
        return jdbc.update("""
            UPDATE runrun_v2.app_users
            SET v2_admin_enabled = ?,
                v2_admin_security_version = ?,
                version = version + 1,
                updated_at = now()
            WHERE id = ?
            """, enabled, next, userId);
    }

    public int invalidateAdminSessions(long id) {
        return jdbc.update("""
            UPDATE runrun_v2.user_sessions
            SET invalidated_at = now()
            WHERE user_id = ?
                AND invalidated_at IS NULL
            """, id);
    }

    public int insertAccessAudit(long administratorId, String action, long targetId, String requestId, String reason, String metadata) {
        return jdbc.update("""
            INSERT INTO runrun_v2.admin_audit_logs (
                administrator_id,
                action,
                target_type,
                target_id,
                request_id,
                reason,
                metadata
            )
            VALUES (
                ?,
                ?,
                'APP_USER',
                ?,
                ?,
                ?,
                ?::jsonb
            )
            """, administratorId, action, targetId, requestId, reason, metadata);
    }

    public List<Map<String, Object>> findAccessAudit(long actor, String action, String requestId) {
        return jdbc.queryForList("""
            SELECT target_id,
                reason,
                metadata::text AS data
            FROM runrun_v2.admin_audit_logs
            WHERE administrator_id = ?
                AND action = ?
                AND request_id = ?
            """, actor, action, requestId);
    }
}
