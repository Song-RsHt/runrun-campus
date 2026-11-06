package com.zhangxuan.runrunv2.mapper.order;

import static com.zhangxuan.runrunv2.dto.response.order.OrderModels.*;

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

  public String createValueV(
      String orderNo,
      Long userId,
      String serviceType,
      Object zoneId,
      Object servicePointId,
      String addressSnapshot,
      String pricingSnapshot,
      String policySnapshot,
      long originalFeeCents,
      long effectiveFeeCents,
      OffsetDateTime pendingPaymentDeadline,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
    return jdbc.queryForObject(
        "INSERT INTO"
            + " runrun_v2.v2_orders(order_no,user_id,service_type,zone_id,service_point_id,address_snapshot,pricing_snapshot,policy_snapshot,original_fee_cents,effective_fee_cents,pending_payment_deadline,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?,?,?,?,?) RETURNING id::text",
        String.class,
        orderNo,
        userId,
        serviceType,
        zoneId,
        servicePointId,
        addressSnapshot,
        pricingSnapshot,
        policySnapshot,
        originalFeeCents,
        effectiveFeeCents,
        pendingPaymentDeadline,
        createdAt,
        updatedAt);
  }

  public int createWriteV(Long orderId, Long actorId, String requestId, String payload) {
    return jdbc.update(
        "INSERT INTO runrun_v2.v2_order_events(order_id,actor_id,event_type,request_id,payload)"
            + " VALUES(?,?,'ORDER_CREATED',?,?::jsonb)",
        orderId,
        actorId,
        requestId,
        payload);
  }
}
