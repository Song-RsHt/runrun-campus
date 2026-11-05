package com.zhangxuan.runrunv2.mapper.identity;

import org.springframework.stereotype.Repository;

/**
 * SQL for IdentityStartupGuard. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class IdentityStartupMapper {
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public IdentityStartupMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public java.lang.Boolean runValueStatement() {
    return jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE"
            + " n.nspname='public' AND c.relkind IN ('r','p','v','m','S','f'))",
        Boolean.class);
  }

  public java.lang.Long runValueAppUsers() {
    return jdbc.queryForObject(
        "SELECT count(controlled_identity) FROM runrun_v2.app_users", Long.class);
  }

  public java.lang.Long runValueIdentityTickets() {
    return jdbc.queryForObject("SELECT count(*) FROM runrun_v2.identity_tickets", Long.class);
  }
}
