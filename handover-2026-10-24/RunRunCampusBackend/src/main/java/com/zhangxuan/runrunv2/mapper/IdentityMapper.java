package com.zhangxuan.runrunv2.mapper;

import com.zhangxuan.runrunv2.model.IdentityUser;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** 新系统身份与会话数据访问；使用调用方事务绑定的 JDBC 连接，不开启或提交事务。 */
@Repository
public class IdentityMapper {

    private static final RowMapper<IdentityUser> USER =
            (r, n) ->
                    new IdentityUser(
                            r.getLong("id"),
                            r.getString("role"),
                            r.getString("status"),
                            r.getBoolean("v2_admin_enabled"),
                            r.getLong("v2_admin_security_version"),
                            r.getBoolean("controlled_identity"),
                            r.getString("password_hash"));

    private final JdbcTemplate jdbc;

    public IdentityMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 读取并锁定 runrun_v2.app_users 的匹配行；锁持有至调用方事务结束。 */
    public List<IdentityUser> findWechatUsersForUpdate(String appId, String digest) {
        return jdbc.query(
                """
                SELECT u.*
                FROM runrun_v2.app_users u
                JOIN runrun_v2.wechat_identities w
                ON w.user_id = u.id
                WHERE w.app_id = ?
                    AND w.openid_hash = ?
                FOR UPDATE OF u
                """,
                USER,
                appId,
                digest);
    }

    public int insertWechatTicket(
            String tokenHash, String appId, String openidHash, String openidCiphertext) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.identity_tickets (
                    token_hash,
                    kind,
                    app_id,
                    openid_hash,
                    openid_ciphertext,
                    expires_at
                )
                VALUES (
                    ?,
                    'WECHAT_LOGIN',
                    ?,
                    ?,
                    ?,
                    now() + interval '5 minutes'
                )
                """,
                tokenHash,
                appId,
                openidHash,
                openidCiphertext);
    }

    public Long countWechatIdentity(String appId, String openidHash) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.wechat_identities
                WHERE app_id = ?
                    AND openid_hash = ?
                """,
                Long.class,
                appId,
                openidHash);
    }

    /** 读取并锁定 runrun_v2.app_users 的匹配行；锁持有至调用方事务结束。 */
    public List<IdentityUser> findPhoneUsersForUpdate(String phone) {
        return jdbc.query(
                """
                SELECT *
                FROM runrun_v2.app_users
                WHERE phone = ?
                FOR UPDATE
                """,
                USER,
                phone);
    }

    public Long insertAccount(String phone, String role) {
        return jdbc.queryForObject(
                """
                INSERT INTO runrun_v2.app_users (
                    phone,
                    role
                )
                VALUES (
                    ?,
                    ?
                )
                RETURNING id
                """,
                Long.class,
                phone,
                role);
    }

    public int insertPendingCertification(Long courierId) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.courier_certifications (
                    courier_id
                )
                VALUES (
                    ?
                )
                """,
                courierId);
    }

    public Long countUserWechatBindings(String appId, long userId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.wechat_identities
                WHERE app_id = ?
                    AND user_id = ?
                """,
                Long.class,
                appId,
                userId);
    }

    public int insertWechatBinding(
            long userId, String appId, String openidHash, String openidCiphertext) {
        return jdbc.update(
                """
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
                """,
                userId,
                appId,
                openidHash,
                openidCiphertext);
    }

    public int insertWechatBoundEvent(long actorId, long subjectId) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.identity_audit_events (
                    actor_id,
                    event_type,
                    subject_id
                )
                VALUES (
                    ?,
                    'WECHAT_BOUND',
                    ?
                )
                """,
                actorId,
                subjectId);
    }

    /** 读取并锁定 runrun_v2.app_users 的匹配行；锁持有至调用方事务结束。 */
    public List<IdentityUser> findPasswordUsersForUpdate(String phone) {
        return jdbc.query(
                """
                SELECT *
                FROM runrun_v2.app_users
                WHERE phone = ?
                FOR UPDATE
                """,
                USER,
                phone);
    }

    public Long countPasswordUserBindings(long userId, String appId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM runrun_v2.wechat_identities
                WHERE user_id = ?
                    AND app_id = ?
                """,
                Long.class,
                userId,
                appId);
    }

    public List<IdentityUser> findActiveSessionUser(String hash) {
        return jdbc.query(
                """
                SELECT u.*
                FROM runrun_v2.user_sessions s
                JOIN runrun_v2.app_users u
                ON u.id = s.user_id
                WHERE s.token_hash = ?
                    AND s.invalidated_at IS NULL
                    AND s.expires_at > now()
                    AND s.security_version = u.v2_admin_security_version
                """,
                USER,
                hash);
    }

    public int invalidateUserSessions(Long accountId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.user_sessions
                SET invalidated_at = now()
                WHERE user_id = ?
                    AND invalidated_at IS NULL
                """,
                accountId);
    }

    public int invalidateSession(String hash) {
        return jdbc.update(
                """
                UPDATE runrun_v2.user_sessions
                SET invalidated_at = now()
                WHERE token_hash = ?
                    AND invalidated_at IS NULL
                """,
                hash);
    }

    public int insertRevocationEvent(Long actorId, Long subjectId) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.identity_audit_events (
                    actor_id,
                    event_type,
                    subject_id
                )
                VALUES (
                    ?,
                    'SESSION_REVOKED',
                    ?
                )
                """,
                actorId,
                subjectId);
    }

    public int updatePassword(String passwordHash, long userId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.app_users
                SET password_hash = ?,
                    version = version + 1,
                    updated_at = now()
                WHERE id = ?
                """,
                passwordHash,
                userId);
    }

    public int invalidatePasswordUserSessions(long userId) {
        return jdbc.update(
                """
                UPDATE runrun_v2.user_sessions
                SET invalidated_at = now()
                WHERE user_id = ?
                    AND invalidated_at IS NULL
                """,
                userId);
    }

    public int insertPasswordRevocationEvent(long actorId, long subjectId) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.identity_audit_events (
                    actor_id,
                    event_type,
                    subject_id
                )
                VALUES (
                    ?,
                    'SESSION_REVOKED',
                    ?
                )
                """,
                actorId,
                subjectId);
    }

    public List<String> findIdentityRequestHashes(Long id, String scope, String key) {
        return jdbc.query(
                """
                SELECT request_hash
                FROM runrun_v2.idempotency_requests
                WHERE actor_id = ?
                    AND scope = ?
                    AND key = ?
                """,
                (r, n) -> r.getString(1),
                id,
                scope,
                key);
    }

    public int insertIdentityRequest(Long actorId, String scope, String key, String requestHash) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.idempotency_requests (
                    actor_id,
                    scope,
                    key,
                    request_hash,
                    status
                )
                VALUES (
                    ?,
                    ?,
                    ?,
                    ?,
                    'PROCESSING'
                )
                """,
                actorId,
                scope,
                key,
                requestHash);
    }

    public int completeIdentityRequest(Long id, String scope, String key) {
        return jdbc.update(
                """
                UPDATE runrun_v2.idempotency_requests
                SET status = 'SUCCEEDED',
                    updated_at = now()
                WHERE actor_id = ?
                    AND scope = ?
                    AND key = ?
                """,
                id,
                scope,
                key);
    }

    /** 读取并锁定 runrun_v2.app_users 的匹配行；锁持有至调用方事务结束。 */
    public List<IdentityUser> findUser(boolean locked, long id) {
        return jdbc.query(
                """
                SELECT *
                FROM runrun_v2.app_users
                WHERE id = ?
                """
                        + (locked
                                ? """
                                  FOR UPDATE
                                  """
                                : ""),
                USER,
                id);
    }

    public int insertSession(long userId, String tokenHash, long securityVersion) {
        return jdbc.update(
                """
                INSERT INTO runrun_v2.user_sessions (
                    user_id,
                    token_hash,
                    expires_at,
                    security_version
                )
                VALUES (
                    ?,
                    ?,
                    now() + interval '24 hours',
                    ?
                )
                """,
                userId,
                tokenHash,
                securityVersion);
    }

    public Boolean isCourierEligible(long userId) {
        return jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM runrun_v2.courier_certifications
                    WHERE courier_id = ?
                        AND eligible)
                """,
                Boolean.class,
                userId);
    }

    /** 读取并锁定 runrun_v2.identity_tickets 的匹配行；锁持有至调用方事务结束。 */
    public List<Map<String, Object>> findTicketForUpdate(String hash, String kind) {
        return jdbc.queryForList(
                """
                SELECT *
                FROM runrun_v2.identity_tickets
                WHERE token_hash = ?
                    AND kind = ?
                    AND consumed_at IS NULL
                    AND expires_at > now()
                FOR UPDATE
                """,
                hash,
                kind);
    }

    public int consumeTicket(String hash) {
        return jdbc.update(
                """
                UPDATE runrun_v2.identity_tickets
                SET consumed_at = now()
                WHERE token_hash = ?
                """,
                hash);
    }

    public List<Object> lockIdentityScope(String key) {
        return jdbc.query(
                """
                SELECT pg_advisory_xact_lock(hashtextextended(?, 0))
                """,
                (RowMapper<Object>) (r, n) -> null,
                key);
    }
}
