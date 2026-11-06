package com.zhangxuan.runruncampus.mapper.admin;

import com.zhangxuan.runruncampus.dto.response.commandcenter.CourierItem;
import com.zhangxuan.runruncampus.dto.response.commandcenter.OrderItem;
import com.zhangxuan.runruncampus.enums.commandcenter.Queue;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * SQL for AdminCommandCenterService. Uses the caller's transaction-bound JDBC connection; never
 * opens or commits a transaction.
 */
@Repository
public class AdminCommandCenterMapper {

  private static final String COURIER_CTE =
      """
      with online_couriers as (
          select u.id, u.display_name, u.courier_availability_updated_at,
              (select count(*) from campus_orders o where o.courier_id = u.id
               and o.status in ('ACCEPTED', 'PICKED_UP')) as active_orders
          from app_users u left join courier_certifications cert on cert.courier_id = u.id
          where u.role = 'COURIER' and u.enabled = true
              and u.courier_approved is distinct from false and u.courier_accepting_orders = true
              and (cert.approved_valid_until is null
                   or cert.approved_valid_until >= (now() at time zone 'Asia/Shanghai')::date)
      )
      """;

  // Payment and event subqueries return one timestamp per order, even for multiple payments/events.
  private static final String ORDER_CTE =
      """
      with stages as (
          select o.id, o.order_no, o.status, o.destination, u.display_name as courier_name,
              o.created_at, o.fee_cents,
              case when o.status = 'PENDING_ASSIGNMENT'
                            and o.payment_method in ('CASH_ON_DELIVERY', 'PERSONAL_QR_MANUAL')
                  then o.created_at
              when o.status = 'PENDING_ASSIGNMENT' then
                  (select max(p.paid_at) from wechat_payments p
                   where p.order_id = o.id and p.status = 'PAID')
              when o.status = 'PENDING_REASSIGNMENT' then
                  (select max(e.created_at) from order_events e
                   where e.order_id = o.id and e.status = 'PENDING_REASSIGNMENT')
              else (select min(e.created_at) from order_events e
                    where e.order_id = o.id and e.status = 'ACCEPTED') end as recorded_start
          from campus_orders o left join app_users u on u.id = o.courier_id
          where (o.status in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')
                 and (o.payment_status = 'PAID'
                      or o.payment_method in ('CASH_ON_DELIVERY', 'PERSONAL_QR_MANUAL')))
             or o.status in ('ACCEPTED', 'PICKED_UP')
      ), queue_orders as (
          select *, coalesce(recorded_start, created_at) as stage_start,
              (recorded_start is null and not (status = 'PENDING_ASSIGNMENT' and fee_cents = 0))
                  as estimated
          from stages
      )
      """;

  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public AdminCommandCenterMapper(org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public long[] countOrderQueues(Timestamp waitCutoff, Timestamp deliveryCutoff) {
    return jdbc.queryForObject(
        ORDER_CTE
            + """
              \sselect count(*) filter (
                  where status in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')),
                  count(*) filter (
                  where status in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')
                      and stage_start <= ?),
                  count(*) filter (
                  where status not in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')),
                  count(*) filter (
                  where status not in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')
                      and stage_start <= ?)
              from queue_orders
              """,
        (rs, row) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)},
        waitCutoff,
        deliveryCutoff);
  }

  public List<OrderItem> pageOrderQueue(
      Queue queue, Timestamp waitCutoff, Timestamp deliveryCutoff, int limit, int offset) {
    String condition =
        switch (queue) {
          case WAITING -> "status in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')";
          case LATE_WAITING ->
              """
              status in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')
                  and stage_start <= ?
              """;
          case ACTIVE -> "status not in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')";
          case LATE_DELIVERY ->
              """
              status not in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')
                  and stage_start <= ?
              """;
        };
    String sql =
        ORDER_CTE
            + """
              \sselect *
              from queue_orders
              where
              """
            + condition
            + """
              \sorder by stage_start asc,
                  id asc
              limit ?
              offset ?
              """;
    Object[] args =
        switch (queue) {
          case LATE_WAITING -> new Object[] {waitCutoff, limit, offset};
          case LATE_DELIVERY -> new Object[] {deliveryCutoff, limit, offset};
          default -> new Object[] {limit, offset};
        };
    return jdbc.query(
        sql,
        (rs, row) ->
            new OrderItem(
                rs.getLong("id"),
                rs.getString("order_no"),
                rs.getString("status"),
                rs.getString("destination"),
                rs.getString("courier_name"),
                rs.getTimestamp("stage_start").toInstant(),
                rs.getBoolean("estimated")),
        args);
  }

  public long[] countOnlineCouriers() {
    return jdbc.queryForObject(
        COURIER_CTE
            + """
              \sselect count(*),
                  count(*) filter (
                  where active_orders = 0)
              from online_couriers
              """,
        (rs, row) -> new long[] {rs.getLong(1), rs.getLong(2)});
  }

  public List<CourierItem> pageOnlineCouriers(int limit, int offset) {
    return jdbc.query(
        COURIER_CTE
            + """
              \sselect *
              from online_couriers
              order by active_orders asc,
                  courier_availability_updated_at asc nulls last,
                  id asc
              limit ?
              offset ?
              """,
        (rs, row) ->
            new CourierItem(
                rs.getLong("id"),
                rs.getString("display_name"),
                rs.getLong("active_orders"),
                rs.getTimestamp("courier_availability_updated_at").toInstant()),
        limit,
        offset);
  }
}
