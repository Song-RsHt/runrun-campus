package com.zhangxuan.runrunv2.mapper.identity;

import com.zhangxuan.runrunv2.model.identity.IdentityUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * SQL for IdentityService. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
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

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public IdentityMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public java.util.List<com.zhangxuan.runrunv2.model.identity.IdentityUser>
      findWechatUsersForUpdate(java.lang.String appId, java.lang.String digest) {
    return jdbc.query(
        "SELECT u.* FROM runrun_v2.app_users u JOIN runrun_v2.wechat_identities w ON w.user_id=u.id"
            + " WHERE w.app_id=? AND w.openid_hash=? FOR UPDATE OF u",
        USER,
        appId,
        digest);
  }

  public int insertWechatTicket(
      java.lang.String parameter1,
      java.lang.String appId,
      java.lang.String digest,
      java.lang.String parameter4) {
    return jdbc.update(
        "INSERT INTO"
            + " runrun_v2.identity_tickets(token_hash,kind,app_id,openid_hash,openid_ciphertext,expires_at)"
            + " VALUES(?,'WECHAT_LOGIN',?,?,?,now()+interval '5 minutes')",
        parameter1,
        appId,
        digest,
        parameter4);
  }

  public java.lang.Long countWechatIdentity(java.lang.String appId, java.lang.Object parameter3) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.wechat_identities WHERE app_id=? AND openid_hash=?",
        Long.class,
        appId,
        parameter3);
  }

  public java.util.List<com.zhangxuan.runrunv2.model.identity.IdentityUser> findPhoneUsersForUpdate(
      java.lang.String phone) {
    return jdbc.query("SELECT * FROM runrun_v2.app_users WHERE phone=? FOR UPDATE", USER, phone);
  }

  public java.lang.Long insertAccount(java.lang.String phone, java.lang.String requestedRole) {
    return jdbc.queryForObject(
        "INSERT INTO runrun_v2.app_users(phone,role) VALUES(?,?) RETURNING id",
        Long.class,
        phone,
        requestedRole);
  }

  public int insertPendingCertification(java.lang.Long id) {
    return jdbc.update("INSERT INTO runrun_v2.courier_certifications(courier_id) VALUES(?)", id);
  }

  public java.lang.Long countUserWechatBindings(java.lang.String appId, long parameter3) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.wechat_identities WHERE app_id=? AND user_id=?",
        Long.class,
        appId,
        parameter3);
  }

  public int insertWechatBinding(
      long parameter1,
      java.lang.String appId,
      java.lang.Object parameter3,
      java.lang.Object parameter4) {
    return jdbc.update(
        "INSERT INTO runrun_v2.wechat_identities(user_id,app_id,openid_hash,openid_ciphertext)"
            + " VALUES(?,?,?,?)",
        parameter1,
        appId,
        parameter3,
        parameter4);
  }

  public int insertWechatBoundEvent(long parameter1, long parameter2) {
    return jdbc.update(
        "INSERT INTO runrun_v2.identity_audit_events(actor_id,event_type,subject_id)"
            + " VALUES(?,'WECHAT_BOUND',?)",
        parameter1,
        parameter2);
  }

  public java.util.List<com.zhangxuan.runrunv2.model.identity.IdentityUser>
      findPasswordUsersForUpdate(java.lang.String phone) {
    return jdbc.query("SELECT * FROM runrun_v2.app_users WHERE phone=? FOR UPDATE", USER, phone);
  }

  public java.lang.Long countPasswordUserBindings(long parameter2, java.lang.String appId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM runrun_v2.wechat_identities WHERE user_id=? AND app_id=?",
        Long.class,
        parameter2,
        appId);
  }

  public java.util.List<com.zhangxuan.runrunv2.model.identity.IdentityUser> findActiveSessionUser(
      java.lang.String parameter2) {
    return jdbc.query(
        "SELECT u.* FROM runrun_v2.user_sessions s JOIN runrun_v2.app_users u ON u.id=s.user_id"
            + " WHERE s.token_hash=? AND s.invalidated_at IS NULL AND s.expires_at>now() AND"
            + " s.security_version=u.v2_admin_security_version",
        USER,
        parameter2);
  }

  public int invalidateUserSessions(java.lang.Long parameter1) {
    return jdbc.update(
        "UPDATE runrun_v2.user_sessions SET invalidated_at=now() WHERE user_id=? AND invalidated_at"
            + " IS NULL",
        parameter1);
  }

  public int invalidateSession(java.lang.String parameter1) {
    return jdbc.update(
        "UPDATE runrun_v2.user_sessions SET invalidated_at=now() WHERE token_hash=? AND"
            + " invalidated_at IS NULL",
        parameter1);
  }

  public int insertRevocationEvent(java.lang.Long parameter1, java.lang.Long parameter2) {
    return jdbc.update(
        "INSERT INTO runrun_v2.identity_audit_events(actor_id,event_type,subject_id)"
            + " VALUES(?,'SESSION_REVOKED',?)",
        parameter1,
        parameter2);
  }

  public int updatePassword(java.lang.String parameter1, long parameter2) {
    return jdbc.update(
        "UPDATE runrun_v2.app_users SET password_hash=?,version=version+1,updated_at=now() WHERE"
            + " id=?",
        parameter1,
        parameter2);
  }

  public int invalidatePasswordUserSessions(long parameter1) {
    return jdbc.update(
        "UPDATE runrun_v2.user_sessions SET invalidated_at=now() WHERE user_id=? AND invalidated_at"
            + " IS NULL",
        parameter1);
  }

  public int insertPasswordRevocationEvent(long parameter1, long parameter2) {
    return jdbc.update(
        "INSERT INTO runrun_v2.identity_audit_events(actor_id,event_type,subject_id)"
            + " VALUES(?,'SESSION_REVOKED',?)",
        parameter1,
        parameter2);
  }

  public java.util.List<java.lang.String> findIdentityRequestHashes(
      java.lang.Long parameter2, java.lang.String scope, java.lang.String key) {
    return jdbc.query(
        "SELECT request_hash FROM runrun_v2.idempotency_requests WHERE actor_id=? AND scope=? AND"
            + " key=?",
        (r, n) -> r.getString(1),
        parameter2,
        scope,
        key);
  }

  public int insertIdentityRequest(
      java.lang.Long parameter1,
      java.lang.String scope,
      java.lang.String key,
      java.lang.String digest) {
    return jdbc.update(
        "INSERT INTO runrun_v2.idempotency_requests(actor_id,scope,key,request_hash,status)"
            + " VALUES(?,?,?,?,'PROCESSING')",
        parameter1,
        scope,
        key,
        digest);
  }

  public int completeIdentityRequest(
      java.lang.Long parameter1, java.lang.String scope, java.lang.String key) {
    return jdbc.update(
        "UPDATE runrun_v2.idempotency_requests SET status='SUCCEEDED',updated_at=now() WHERE"
            + " actor_id=? AND scope=? AND key=?",
        parameter1,
        scope,
        key);
  }

  public java.util.List<com.zhangxuan.runrunv2.model.identity.IdentityUser> findUser(
      boolean locked, long id) {
    return jdbc.query(
        "SELECT * FROM runrun_v2.app_users WHERE id=?" + (locked ? " FOR UPDATE" : ""), USER, id);
  }

  public int insertSession(long parameter1, java.lang.String parameter2, long parameter3) {
    return jdbc.update(
        "INSERT INTO runrun_v2.user_sessions(user_id,token_hash,expires_at,security_version)"
            + " VALUES(?,?,now()+interval '24 hours',?)",
        parameter1,
        parameter2,
        parameter3);
  }

  public java.lang.Boolean isCourierEligible(long parameter2) {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM runrun_v2.courier_certifications WHERE courier_id=? AND"
            + " eligible)",
        Boolean.class,
        parameter2);
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findTicketForUpdate(
      java.lang.String parameter1, java.lang.String kind) {
    return jdbc.queryForList(
        "SELECT * FROM runrun_v2.identity_tickets WHERE token_hash=? AND kind=? AND consumed_at IS"
            + " NULL AND expires_at>now() FOR UPDATE",
        parameter1,
        kind);
  }

  public int consumeTicket(java.lang.String parameter1) {
    return jdbc.update(
        "UPDATE runrun_v2.identity_tickets SET consumed_at=now() WHERE token_hash=?", parameter1);
  }

  public java.util.List<java.lang.Object> lockIdentityScope(java.lang.String key) {
    return jdbc.query(
        "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
        (RowMapper<Object>) (r, n) -> null,
        key);
  }
}
