package com.zhangxuan.runrunv2.service.impl;

import com.zhangxuan.runrunv2.exception.IdentityFailure;
import com.zhangxuan.runrunv2.mapper.FulfillmentQueryMapper;
import com.zhangxuan.runrunv2.model.FulfillmentTaskRow;
import com.zhangxuan.runrunv2.model.ResourceIds;
import com.zhangxuan.runrunv2.security.IdentitySecrets;
import com.zhangxuan.runrunv2.service.FulfillmentQueryService;
import com.zhangxuan.runrunv2.service.IdentityService;
import com.zhangxuan.runrunv2.vo.CourierTaskVO;
import com.zhangxuan.runrunv2.vo.CursorPageVO;
import com.zhangxuan.runrunv2.vo.FulfillmentOrderVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

















































/**
 * 将数据库任务事实转换为当前参与人的可见任务及允许操作。游标签名和操作资格属于业务策略，不下放给 SQL 行映射。
 */
@Service
public class FulfillmentQueryServiceImpl implements FulfillmentQueryService {

    private final IdentityService identity;

    private final IdentitySecrets secrets;

    private final FulfillmentQueryMapper mapper;

    public FulfillmentQueryServiceImpl(IdentityService identity, IdentitySecrets secrets, FulfillmentQueryMapper mapper) {
        this.identity = identity;
        this.secrets = secrets;
        this.mapper = mapper;
    }

    private record Cursor(String createdAt, String id) {
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<CourierTaskVO> tasks(String token, String cursor, Integer requestedLimit, String status) {
        var actor = identity.requireRole(token, "COURIER", false);
        int limit = limit(requestedLimit);
        if (status != null && !Set.of("OFFERED", "ACTIVE", "RELEASED", "HANDED_OVER", "DELIVERED", "CANCELLED").contains(status))
            throw IdentityFailure.invalid();
        String scope = "tasks:" + actor.id() + ":" + Objects.toString(status, "");
        Cursor after = decode(scope, cursor);
        List<CourierTaskVO> rows = mapper.findCourierTasks(Long.valueOf(actor.id()), status, after == null ? null : OffsetDateTime.parse(after.createdAt()), after == null ? null : Long.valueOf(after.id()), limit + 1).stream().map(row -> taskItem(row, actor.courierEligible())).toList();
        boolean more = rows.size() > limit;
        List<CourierTaskVO> items = List.copyOf(rows.subList(0, Math.min(limit, rows.size())));
        CourierTaskVO last = items.isEmpty() ? null : items.get(items.size() - 1);
        return new CursorPageVO<>(items, more ? encode(scope, last.createdAt(), last.taskId()) : null);
    }

    @Transactional(readOnly = true)
    @Override
    public CursorPageVO<FulfillmentOrderVO> pool(String token, String cursor, Integer requestedLimit) {
        var actor = identity.requireRole(token, "COURIER", true);
        int limit = limit(requestedLimit);
        String scope = "pool:" + actor.id();
        Cursor after = decode(scope, cursor);
        var rows = mapper.findPaidOrderPool(after == null ? null : OffsetDateTime.parse(after.createdAt()), after == null ? null : Long.valueOf(after.id()), limit + 1);
        boolean more = rows.size() > limit;
        var items = rows.subList(0, Math.min(limit, rows.size())).stream().map(r -> new FulfillmentOrderVO(r.get("id").toString(), r.get("order_no").toString(), 2, r.get("status").toString(), r.get("status").toString(), ((Number) r.get("version")).longValue(), ((Number) r.get("original_fee_cents")).longValue(), ((Number) r.get("effective_fee_cents")).longValue(), null, null, null)).toList();
        var last = rows.isEmpty() ? null : rows.get(Math.min(limit, rows.size()) - 1);
        OffsetDateTime created = last == null ? null : ((Timestamp) last.get("created_at")).toInstant().atOffset(ZoneOffset.UTC);
        return new CursorPageVO<>(items, more ? encode(scope, created, last.get("id").toString()) : null);
    }

    // C01: task history is readable without eligibility; write actions require current eligibility.
    private CourierTaskVO taskItem(FulfillmentTaskRow row, boolean eligible) {
        var actions = new ArrayList<String>();
        if (!row.superseded()) {
            if (row.taskStatus().equals("OFFERED") && row.orderStatus().equals("EXCEPTION")) {
                actions.add("DECLINE");
                if (eligible)
                    actions.add("ACCEPT");
            }
            if (eligible) {
                if (row.taskStatus().equals("ACTIVE") && row.orderStatus().equals("ACCEPTED")) {
                    actions.add("UPLOAD_PICKUP");
                    actions.add("PICKUP");
                    actions.add("ARRIVAL");
                    actions.add("SPECIFICATION");
                }
                if (row.taskStatus().equals("ACTIVE") && row.orderStatus().equals("PICKED_UP")) {
                    actions.add("UPLOAD_DELIVERY");
                    actions.add("DELIVERY_LOCATION");
                    actions.add("DELIVER");
                    actions.add("SPECIFICATION");
                }
                if (row.taskStatus().equals("DELIVERED") && row.orderStatus().equals("DELIVERED_PENDING_CONFIRMATION"))
                    actions.add("SPECIFICATION");
            }
        }
        return new CourierTaskVO(row.taskId(), row.taskStatus(), row.taskVersion(), 2, row.orderId(), row.orderNo(), row.orderStatus(), row.orderVersion(), row.createdAt(), actions);
    }

    private int limit(Integer value) {
        int limit = value == null ? 20 : value;
        if (limit < 1 || limit > 100)
            throw IdentityFailure.invalid();
        return limit;
    }

    private String encode(String scope, OffsetDateTime created, String id) {
        String payload = created + "|" + id;
        String body = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return body + "." + secrets.hash("fulfillment-cursor:" + scope, body);
    }

    private Cursor decode(String scope, String value) {
        if (value == null)
            return null;
        try {
            if (value.length() > 512)
                throw IdentityFailure.invalid();
            String[] parts = value.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(secrets.hash("fulfillment-cursor:" + scope, parts[0]).getBytes(StandardCharsets.UTF_8), parts[1].getBytes(StandardCharsets.UTF_8)))
                throw IdentityFailure.invalid();
            String[] payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\\|", -1);
            if (payload.length != 2)
                throw IdentityFailure.invalid();
            OffsetDateTime.parse(payload[0]);
            ResourceIds.parse(payload[1]);
            return new Cursor(payload[0], payload[1]);
        } catch (RuntimeException ex) {
            throw IdentityFailure.invalid();
        }
    }
}
