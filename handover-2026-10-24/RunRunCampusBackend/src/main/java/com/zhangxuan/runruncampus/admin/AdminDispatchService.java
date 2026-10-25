package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderEvent;
import com.zhangxuan.runruncampus.order.OrderEventRepository;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AdminDispatchService {
    static final int PAGE_SIZE = 20;

    private static final String CANDIDATE_SQL = """
            select u.id, u.display_name, u.courier_availability_updated_at,
                coalesce(metrics.active_orders, 0) as active_orders,
                coalesce(metrics.completed_orders, 0) as completed_orders,
                coalesce(metrics.matched_completed_orders, 0) as matched_completed_orders,
                metrics.average_rating, coalesce(metrics.rating_count, 0) as rating_count
            from app_users u
            left join courier_certifications cert on cert.courier_id = u.id
            left join lateral (
                select count(*) filter (where history.status in ('ACCEPTED', 'PICKED_UP')) as active_orders,
                    count(*) filter (where history.status = 'COMPLETED') as completed_orders,
                    count(*) filter (where history.status = 'COMPLETED' and history.service_type = ?) as matched_completed_orders,
                    avg(history.rating_score) filter (where history.rating_score is not null) as average_rating,
                    count(history.rating_score) as rating_count
                from campus_orders history where history.courier_id = u.id
            ) metrics on true
            where u.role = 'COURIER' and u.enabled = true
              and u.courier_approved is distinct from false
              and u.courier_accepting_orders = true
              and coalesce(metrics.active_orders, 0) < ?
              and (cert.approved_valid_until is null
                   or cert.approved_valid_until >= (now() at time zone 'Asia/Shanghai')::date)
            """;

    private final JdbcTemplate jdbc;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderEventRepository eventRepository;
    private final OrderMessageService messageService;
    private final AdminAuditService auditService;
    private final PlatformSettingsService settingsService;
    private final CourierCertificationService certificationService;

    public AdminDispatchService(JdbcTemplate jdbc, OrderRepository orderRepository, UserRepository userRepository,
                                OrderEventRepository eventRepository, OrderMessageService messageService,
                                AdminAuditService auditService, PlatformSettingsService settingsService,
                                CourierCertificationService certificationService) {
        this.jdbc = jdbc;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.messageService = messageService;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.certificationService = certificationService;
    }

    @Transactional(readOnly = true)
    public Candidates candidates(Long orderId, int requestedPage) {
        if (requestedPage < 0 || requestedPage > 100000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码超出范围");
        }
        CampusOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        requireDispatchable(order);
        Instant now = Instant.now();
        List<Candidate> ranked = jdbc.query(CANDIDATE_SQL, (rs, row) -> {
            double rating = rs.getDouble("average_rating");
            Double averageRating = rs.wasNull() ? null : rating;
            Timestamp availability = rs.getTimestamp("courier_availability_updated_at");
            Instant onlineSince = availability == null ? null : availability.toInstant();
            long onlineMinutes = onlineSince == null ? 0 : Math.max(0, Duration.between(onlineSince, now).toMinutes());
            long activeOrders = rs.getLong("active_orders");
            long completedOrders = rs.getLong("completed_orders");
            long matchedCompletedOrders = rs.getLong("matched_completed_orders");
            long ratingCount = rs.getLong("rating_count");
            int score = recommendationScore(activeOrders, onlineMinutes, completedOrders,
                    matchedCompletedOrders, averageRating, ratingCount);
            return new Candidate(rs.getLong("id"), rs.getString("display_name"), activeOrders, onlineSince,
                    completedOrders, matchedCompletedOrders, averageRating, ratingCount, score, false,
                    recommendationReasons(activeOrders, onlineMinutes, completedOrders,
                            matchedCompletedOrders, averageRating, ratingCount));
        }, order.getServiceType().name(), settingsService.get().maxActiveOrders());
        ranked.sort(Comparator.comparingInt(Candidate::recommendationScore).reversed()
                .thenComparingLong(Candidate::activeOrders)
                .thenComparing(Candidate::onlineSince, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingLong(Candidate::courierId));
        if (!ranked.isEmpty()) {
            Candidate first = ranked.get(0);
            ranked.set(0, new Candidate(first.courierId(), first.displayName(), first.activeOrders(),
                    first.onlineSince(), first.completedOrders(), first.matchedCompletedOrders(),
                    first.averageRating(), first.ratingCount(), first.recommendationScore(), true, first.reasons()));
        }
        long total = ranked.size();
        int page = (int) Math.min(requestedPage, Math.max(0, (total - 1) / PAGE_SIZE));
        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, ranked.size());
        return new Candidates(order.getId(), order.getOrderNo(), now, ranked.subList(from, to),
                page, PAGE_SIZE, total);
    }

    @Transactional
    public DispatchResult dispatch(AppUser administrator, Long orderId, Long courierId) {
        CampusOrder order = orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
        requireDispatchable(order);
        boolean reassignment = order.getStatus() == OrderStatus.PENDING_REASSIGNMENT;
        AppUser courier = userRepository.findLockedById(courierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "骑手不存在"));
        requireAvailableCourier(courier);
        long activeOrders = orderRepository.countByCourierAndStatusIn(courier,
                List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP));
        if (activeOrders >= settingsService.get().maxActiveOrders()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "所选骑手已达到同时接单上限");
        }

        order.assignCourier(courier);
        eventRepository.save(new OrderEvent(order, administrator, order.getStatus()));
        messageService.send(order.getCustomer(), order, OrderMessageType.ORDER_ACCEPTED,
                "骑手已接单", "运营已安排 " + courier.getDisplayName() + " 配送，请留意订单进度");
        messageService.send(courier, order, OrderMessageType.ORDER_ACCEPTED,
                "运营已派单", "订单 " + order.getOrderNo() + " 已派给你，请尽快前往取件");
        auditService.record(administrator, reassignment ? AdminAuditAction.ORDER_REASSIGNED : AdminAuditAction.ORDER_DISPATCHED,
                AdminAuditTargetType.ORDER, order.getId(), "%s订单 %s 派给骑手 %s（#%d）".formatted(
                        reassignment ? "改派" : "将",
                        order.getOrderNo(), courier.getDisplayName(), courier.getId()));
        return new DispatchResult(order.getId(), order.getOrderNo(), order.getStatus(),
                courier.getId(), courier.getDisplayName(), Instant.now());
    }

    private void requireDispatchable(CampusOrder order) {
        if ((order.getStatus() != OrderStatus.PENDING_ASSIGNMENT
                && order.getStatus() != OrderStatus.PENDING_REASSIGNMENT) || order.getCourier() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已被接单，无法再次派单");
        }
        if (!order.canStartFulfillment()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单尚未完成支付");
        }
    }

    private void requireAvailableCourier(AppUser courier) {
        if (courier.getRole() != UserRole.COURIER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选用户不是骑手");
        }
        if (!courier.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "所选骑手账号已停用");
        }
        if (!certificationService.isEligible(courier)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "所选骑手认证未通过或已过期");
        }
        if (!courier.isCourierAcceptingOrders() || courier.getCourierAvailabilityUpdatedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "所选骑手当前未上线接单");
        }
    }

    static int recommendationScore(long activeOrders, long onlineMinutes, long completedOrders,
                                   long matchedCompletedOrders, Double averageRating, long ratingCount) {
        long score = 60;
        score -= Math.min(Math.max(activeOrders, 0), 2) * 45;
        score += Math.min(Math.max(onlineMinutes, 0) / 12, 10);
        score += Math.min(Math.max(completedOrders, 0) / 4, 10);
        score += Math.min(Math.max(matchedCompletedOrders, 0) / 2, 10);
        if (averageRating != null && ratingCount >= 3) {
            score += Math.min(10, Math.max(0, Math.round((averageRating - 3.0) * 5)));
        }
        return (int) Math.min(100, Math.max(0, score));
    }

    private static List<String> recommendationReasons(long activeOrders, long onlineMinutes, long completedOrders,
                                                       long matchedCompletedOrders, Double averageRating,
                                                       long ratingCount) {
        List<String> reasons = new ArrayList<>();
        reasons.add(activeOrders == 0 ? "当前无在途订单" : "当前有 " + activeOrders + " 单进行中");
        if (matchedCompletedOrders > 0) {
            reasons.add("已完成 " + matchedCompletedOrders + " 单同类订单");
        } else if (completedOrders > 0) {
            reasons.add("累计完成 " + completedOrders + " 单");
        } else {
            reasons.add("暂无历史履约记录");
        }
        if (averageRating != null && ratingCount >= 3) {
            reasons.add("历史评分 %.1f（%d 次）".formatted(averageRating, ratingCount));
        }
        reasons.add("已上线 " + onlineMinutes + " 分钟");
        return List.copyOf(reasons);
    }

    public record Candidate(long courierId, String displayName, long activeOrders, Instant onlineSince,
                            long completedOrders, long matchedCompletedOrders, Double averageRating,
                            long ratingCount, int recommendationScore, boolean recommended,
                            List<String> reasons) { }
    public record Candidates(long orderId, String orderNo, Instant generatedAt, List<Candidate> items,
                             int page, int size, long totalElements) { }
    public record DispatchResult(long orderId, String orderNo, OrderStatus status, long courierId,
                                 String courierName, Instant dispatchedAt) { }
}
