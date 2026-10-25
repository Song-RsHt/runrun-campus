package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderPaymentMethod;
import com.zhangxuan.runruncampus.order.OrderPaymentStatus;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.user.AppUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class FulfillmentProvenanceService {
    private final JdbcTemplate jdbc;
    private final OrderRepository orders;

    public FulfillmentProvenanceService(JdbcTemplate jdbc, OrderRepository orders) {
        this.jdbc = jdbc;
        this.orders = orders;
    }

    @Transactional
    public Entry record(AppUser administrator, CreateRequest request) {
        if (request == null || request.channel() == null || request.completedAt() == null
                || request.completedAt().isAfter(Instant.now())
                || request.sourceRef() == null || !request.sourceRef().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
                || !isHash(request.completionEvidenceSha256()) || !optionalHash(request.financialEvidenceSha256())
                || negative(request.customerPaidCents()) || negative(request.platformRevenueCents())
                || negative(request.courierSettlementCents())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "溯源字段格式无效");
        }
        boolean hasAmounts = request.customerPaidCents() != null || request.platformRevenueCents() != null
                || request.courierSettlementCents() != null;
        if (hasAmounts && request.financialEvidenceSha256() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "填写金额时必须提供独立的财务凭证摘要");
        }
        if (request.channel() == Channel.APP_END_TO_END && request.appOrderId() == null
                || request.channel() == Channel.MANUAL_OFFLINE && request.appOrderId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "履约渠道与软件订单关联不一致");
        }
        if (request.appOrderId() != null) {
            var order = orders.findById(request.appOrderId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联的软件订单不存在"));
            if (order.getStatus() != OrderStatus.COMPLETED) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联的软件订单尚未完成");
            }
            if (request.channel() == Channel.APP_END_TO_END &&
                    (order.getPaymentMethod() != OrderPaymentMethod.WECHAT_APP
                     || order.getPaymentStatus() != OrderPaymentStatus.PAID)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "全软件闭环须有已支付的软件支付订单");
            }
        }
        try {
            Long id = jdbc.queryForObject("""
                    insert into fulfillment_provenance
                    (source_ref, channel, app_order_id, completed_at, customer_paid_cents,
                     platform_revenue_cents, courier_settlement_cents, completion_evidence_sha256,
                     financial_evidence_sha256, recorded_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id
                    """, Long.class, request.sourceRef(), request.channel().name(), request.appOrderId(),
                    Timestamp.from(request.completedAt()), request.customerPaidCents(), request.platformRevenueCents(),
                    request.courierSettlementCents(), request.completionEvidenceSha256().toLowerCase(Locale.ROOT),
                    request.financialEvidenceSha256() == null ? null
                            : request.financialEvidenceSha256().toLowerCase(Locale.ROOT), administrator.getId());
            return find(id);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该来源编号或软件订单已被记录");
        }
    }

    @Transactional
    public Entry verify(AppUser administrator, long id) {
        int updated = jdbc.update("""
                update fulfillment_provenance set verified_by = ?, verified_at = now()
                where id = ? and verified_by is null and recorded_by <> ?
                """, administrator.getId(), id, administrator.getId());
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "记录不存在、已核验或不能由录入人自行核验");
        }
        return find(id);
    }

    @Transactional(readOnly = true)
    public List<Entry> list(int page) {
        if (page < 0 || page > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码超出范围");
        }
        return jdbc.query("""
                select * from fulfillment_provenance order by id desc limit 100 offset ?
                """, (rs, row) -> map(rs), page * 100);
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        return jdbc.queryForObject("""
                select count(*) as recorded_count,
                       count(*) filter (where verified_at is not null) as verified_count,
                       count(*) filter (where verified_at is not null and platform_revenue_cents is not null)
                           as verified_revenue_count,
                       sum(platform_revenue_cents) filter
                           (where verified_at is not null and platform_revenue_cents is not null)
                           as verified_revenue_cents
                from fulfillment_provenance
                """, (rs, row) -> new Summary(rs.getLong("recorded_count"), rs.getLong("verified_count"),
                rs.getLong("verified_revenue_count"), rs.getBigDecimal("verified_revenue_cents") == null
                        ? null : rs.getBigDecimal("verified_revenue_cents").longValueExact()));
    }

    private Entry find(long id) {
        return jdbc.queryForObject("select * from fulfillment_provenance where id = ?",
                (rs, row) -> map(rs), id);
    }

    private static Entry map(ResultSet rs) throws SQLException {
        return new Entry(rs.getLong("id"), rs.getString("source_ref"), Channel.valueOf(rs.getString("channel")),
                (Long) rs.getObject("app_order_id"), rs.getTimestamp("completed_at").toInstant(),
                (Long) rs.getObject("customer_paid_cents"), (Long) rs.getObject("platform_revenue_cents"),
                (Long) rs.getObject("courier_settlement_cents"), rs.getString("completion_evidence_sha256"),
                rs.getString("financial_evidence_sha256"), rs.getLong("recorded_by"),
                rs.getTimestamp("recorded_at").toInstant(), (Long) rs.getObject("verified_by"),
                rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant());
    }

    private static boolean negative(Long amount) {
        return amount != null && amount < 0;
    }

    private static boolean isHash(String value) {
        return value != null && value.matches("[A-Fa-f0-9]{64}");
    }

    private static boolean optionalHash(String value) {
        return value == null || isHash(value);
    }

    public enum Channel { APP_END_TO_END, APP_ASSISTED, MANUAL_OFFLINE }

    public record CreateRequest(String sourceRef, Channel channel, Long appOrderId, Instant completedAt,
                                Long customerPaidCents, Long platformRevenueCents, Long courierSettlementCents,
                                String completionEvidenceSha256, String financialEvidenceSha256) { }

    public record Entry(long id, String sourceRef, Channel channel, Long appOrderId, Instant completedAt,
                        Long customerPaidCents, Long platformRevenueCents, Long courierSettlementCents,
                        String completionEvidenceSha256, String financialEvidenceSha256, long recordedBy,
                        Instant recordedAt, Long verifiedBy, Instant verifiedAt) { }

    public record Summary(long recordedCount, long verifiedCount, long verifiedRevenueCount,
                          Long verifiedRevenueCents) { }
}
