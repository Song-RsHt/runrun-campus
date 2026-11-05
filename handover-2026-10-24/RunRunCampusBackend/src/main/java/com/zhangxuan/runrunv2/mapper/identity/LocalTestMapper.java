package com.zhangxuan.runrunv2.mapper.identity;

import org.springframework.stereotype.Repository;

/**
 * SQL for LocalTestBootstrap. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class LocalTestMapper {
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public LocalTestMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void runRowsStatement() {
    jdbc.execute("SELECT pg_advisory_xact_lock(7082001)");
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> runRowsAppUsers() {
    return jdbc.queryForList(
        "SELECT password_hash,role,v2_admin_enabled FROM runrun_v2.app_users WHERE"
            + " phone='test-admin'");
  }

  public java.lang.Boolean runValueAdminAuditLogs() {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM runrun_v2.admin_audit_logs WHERE"
            + " request_id='local-test-enable' AND reason='Isolated synthetic local test fixture')",
        Boolean.class);
  }

  public java.lang.Long runValueAppUsers() {
    return jdbc.queryForObject("SELECT count(*) FROM runrun_v2.app_users", Long.class);
  }

  public java.lang.Long runValueAppUsers2(java.lang.String parameter2) {
    return jdbc.queryForObject(
        "INSERT INTO runrun_v2.app_users(phone,role,password_hash) VALUES('test-admin','USER',?)"
            + " RETURNING id",
        Long.class,
        parameter2);
  }

  public int runWriteWechatIdentities(
      long id, java.lang.String app, java.lang.String parameter3, java.lang.String parameter4) {
    return jdbc.update(
        "INSERT INTO runrun_v2.wechat_identities(user_id,app_id,openid_hash,openid_ciphertext)"
            + " VALUES(?,?,?,?)",
        id,
        app,
        parameter3,
        parameter4);
  }
}
