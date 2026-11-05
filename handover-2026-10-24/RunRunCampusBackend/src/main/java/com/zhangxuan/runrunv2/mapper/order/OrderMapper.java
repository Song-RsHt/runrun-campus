package com.zhangxuan.runrunv2.mapper.order;

import static com.zhangxuan.runrunv2.model.order.OrderModels.*;

import java.time.*;
import org.springframework.stereotype.Repository;

/**
 * SQL for OrderService. Uses the caller's transaction-bound JDBC connection; never opens or commits
 * a transaction.
 */
@Repository
public class OrderMapper {
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public OrderMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public java.lang.String createValueV(
      java.lang.String parameter2,
      java.lang.Long parameter3,
      java.lang.String parameter4,
      java.lang.Object parameter5,
      java.lang.Object parameter6,
      java.lang.String parameter7,
      java.lang.String parameter8,
      java.lang.String parameter9,
      long parameter10,
      long parameter11,
      java.time.OffsetDateTime deadline,
      java.time.OffsetDateTime created,
      java.time.OffsetDateTime binding14) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " runrun_v2.v2_orders(order_no,user_id,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?,?,?,?,?) RETURNING id::text",
        String.class,
        parameter2,
        parameter3,
        parameter4,
        parameter5,
        parameter6,
        parameter7,
        parameter8,
        parameter9,
        parameter10,
        parameter11,
        deadline,
        created,
        binding14);
  }

  public int createWriteV(
      java.lang.Long parameter1,
      java.lang.Long parameter2,
      java.lang.String key,
      java.lang.String parameter4) {
    return jdbc.update(
        "INSERT INTO runrun_v2.v2_order_events(order_id,actor_id,event_type,request_id,payload)"
            + " VALUES(?,?,'ORDER_CREATED',?,?::jsonb)",
        parameter1,
        parameter2,
        key,
        parameter4);
  }
}
