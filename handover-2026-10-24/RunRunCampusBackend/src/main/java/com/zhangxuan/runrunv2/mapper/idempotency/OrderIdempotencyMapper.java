package com.zhangxuan.runrunv2.mapper.idempotency;

import org.springframework.stereotype.Repository;

/**
 * SQL for OrderIdempotency. Uses the caller's transaction-bound JDBC connection; never opens or
 * commits a transaction.
 */
@Repository
public class OrderIdempotencyMapper {
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public OrderIdempotencyMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public java.util.List<java.util.Map<java.lang.String, java.lang.Object>> findRequestForUpdate(
      java.lang.Long parameter1, java.lang.String scope, java.lang.String key) {
    return jdbc.queryForList(
        "SELECT request_hash,response_snapshot,status FROM runrun_v2.idempotency_requests WHERE"
            + " actor_id=? AND scope=? AND key=? FOR UPDATE",
        parameter1,
        scope,
        key);
  }

  public int insertRequest(
      java.lang.Long parameter1,
      java.lang.String scope,
      java.lang.String key,
      java.lang.String hash) {
    return jdbc.update(
        "INSERT INTO runrun_v2.idempotency_requests(actor_id,scope,key,request_hash,status)"
            + " VALUES(?,?,?,?,'PROCESSING')",
        parameter1,
        scope,
        key,
        hash);
  }

  public int completeRequest(
      java.lang.String resourceType,
      java.lang.String resourceId,
      java.lang.String parameter3,
      java.lang.Long parameter4,
      java.lang.String scope,
      java.lang.String key) {
    return jdbc.update(
        "UPDATE runrun_v2.idempotency_requests SET"
            + " status='SUCCEEDED',resource_type=?,resource_id=?,response_snapshot=?::jsonb,updated_at=now()"
            + " WHERE actor_id=? AND scope=? AND key=?",
        resourceType,
        resourceId,
        parameter3,
        parameter4,
        scope,
        key);
  }
}
