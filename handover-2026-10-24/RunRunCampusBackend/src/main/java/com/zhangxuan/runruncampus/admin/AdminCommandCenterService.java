package com.zhangxuan.runruncampus.admin;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.zhangxuan.runruncampus.api.PlatformSettingsService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Service
public class AdminCommandCenterService {
    static final int PAGE_SIZE = 20;

    // Payment and event subqueries return one timestamp per order, even for multiple payments/events.
    private static final String ORDER_CTE = """
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
    private static final String COURIER_CTE = """
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

    private final JdbcTemplate jdbc;
    private final PlatformSettingsService settingsService;

    public AdminCommandCenterService(JdbcTemplate jdbc, PlatformSettingsService settingsService) {
        this.jdbc = jdbc;
        this.settingsService = settingsService;
    }

    public enum Queue { WAITING, LATE_WAITING, ACTIVE, LATE_DELIVERY }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Snapshot snapshot(AdminRole role, Queue queue, int orderPage, int courierPage) {
        if (orderPage < 0 || courierPage < 0 || orderPage > 100000 || courierPage > 100000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码超出范围");
        }
        Instant now = Instant.now();
        var settings = settingsService.get();
        Orders orders = role.permits(AdminPermission.ORDER_VIEW)
                ? orders(queue, orderPage, now, settings.waitMinutes(), settings.deliveryMinutes()) : null;
        Couriers couriers = role.permits(AdminPermission.COURIER_VIEW) ? couriers(courierPage) : null;
        return new Snapshot(now, settings.waitMinutes(), settings.deliveryMinutes(), orders, couriers);
    }

    private Orders orders(Queue queue, int requestedPage, Instant now, int waitMinutes, int deliveryMinutes) {
        Timestamp waitCutoff = Timestamp.from(now.minusSeconds(waitMinutes * 60L));
        Timestamp deliveryCutoff = Timestamp.from(now.minusSeconds(deliveryMinutes * 60L));
        long[] counts = jdbc.queryForObject(ORDER_CTE + """
                select count(*) filter (where status in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')),
                       count(*) filter (where status in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT') and stage_start <= ?),
                       count(*) filter (where status not in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')),
                       count(*) filter (where status not in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT') and stage_start <= ?)
                from queue_orders
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)},
                waitCutoff, deliveryCutoff);
        String condition = switch (queue) {
            case WAITING -> "status in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')";
            case LATE_WAITING -> "status in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT') and stage_start <= ?";
            case ACTIVE -> "status not in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT')";
            case LATE_DELIVERY -> "status not in ('PENDING_ASSIGNMENT', 'PENDING_REASSIGNMENT') and stage_start <= ?";
        };
        long total = counts[queue.ordinal()];
        int page = clampPage(requestedPage, total);
        String sql = ORDER_CTE + "select * from queue_orders where " + condition
                + " order by stage_start asc, id asc limit ? offset ?";
        Object[] args = switch (queue) {
            case LATE_WAITING -> new Object[]{waitCutoff, PAGE_SIZE, page * PAGE_SIZE};
            case LATE_DELIVERY -> new Object[]{deliveryCutoff, PAGE_SIZE, page * PAGE_SIZE};
            default -> new Object[]{PAGE_SIZE, page * PAGE_SIZE};
        };
        List<OrderItem> items = jdbc.query(sql, (rs, row) -> new OrderItem(
                rs.getLong("id"), rs.getString("order_no"), rs.getString("status"),
                rs.getString("destination"), rs.getString("courier_name"),
                rs.getTimestamp("stage_start").toInstant(), rs.getBoolean("estimated")), args);
        return new Orders(counts[0], counts[1], counts[2], counts[3], queue, items, page, PAGE_SIZE, total);
    }

    private Couriers couriers(int requestedPage) {
        long[] counts = jdbc.queryForObject(COURIER_CTE + """
                select count(*), count(*) filter (where active_orders = 0) from online_couriers
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getLong(2)});
        int page = clampPage(requestedPage, counts[0]);
        List<CourierItem> items = jdbc.query(COURIER_CTE
                        + "select * from online_couriers order by active_orders asc, courier_availability_updated_at asc nulls last, id asc limit ? offset ?",
                (rs, row) -> new CourierItem(rs.getLong("id"), rs.getString("display_name"),
                        rs.getLong("active_orders"), rs.getTimestamp("courier_availability_updated_at").toInstant()),
                PAGE_SIZE, page * PAGE_SIZE);
        return new Couriers(counts[0], counts[1], items, page, PAGE_SIZE);
    }

    private int clampPage(int page, long total) {
        return (int) Math.min(page, Math.max(0, (total - 1) / PAGE_SIZE));
    }

    public record Snapshot(Instant generatedAt, int waitMinutes, int deliveryMinutes,
                           Orders orders, Couriers couriers) { }
    public record OrderItem(long id, String orderNo, String status, String destination,
                            String courierName, Instant stageStart, boolean estimated) { }
    public record Orders(long waiting, long lateWaiting, long active, long lateDelivery, Queue queue,
                         List<OrderItem> items, int page, int size, long totalElements) { }
    public record CourierItem(long id, String displayName, long activeOrders, Instant onlineSince) { }
    public record Couriers(long online, long idle, List<CourierItem> items, int page, int size) { }
}
