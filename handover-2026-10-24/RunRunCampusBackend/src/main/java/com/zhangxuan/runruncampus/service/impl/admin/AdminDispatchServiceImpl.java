package com.zhangxuan.runruncampus.service.impl.admin;

import com.zhangxuan.runruncampus.constant.admin.AdminDispatchConstants;
import com.zhangxuan.runruncampus.entity.order.CampusOrder;
import com.zhangxuan.runruncampus.entity.order.OrderEvent;
import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditAction;
import com.zhangxuan.runruncampus.enums.admin.AdminAuditTargetType;
import com.zhangxuan.runruncampus.enums.message.OrderMessageType;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.enums.user.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.mapper.admin.AdminDispatchMapper;
import com.zhangxuan.runruncampus.model.admin.DispatchScoreCalculator;
import com.zhangxuan.runruncampus.repository.order.OrderEventRepository;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import com.zhangxuan.runruncampus.repository.user.UserRepository;
import com.zhangxuan.runruncampus.service.admin.AdminAuditService;
import com.zhangxuan.runruncampus.service.admin.AdminDispatchService;
import com.zhangxuan.runruncampus.service.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.service.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.service.message.OrderMessageService;
import com.zhangxuan.runruncampus.vo.admin.DispatchCandidateVO;
import com.zhangxuan.runruncampus.vo.admin.DispatchCandidatesVO;
import com.zhangxuan.runruncampus.vo.admin.DispatchResultVO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理员派单业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。
 *
 * <p>保留 legacy 派单资格、推荐排名及审计规则。Mapper 返回骑手原始事实，在线时长及推荐策略在本层计算。
 */
@Service
public class AdminDispatchServiceImpl implements AdminDispatchService {

    private final AdminDispatchMapper mapper;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderEventRepository eventRepository;
    private final OrderMessageService messageService;
    private final AdminAuditService auditService;
    private final PlatformSettingsService settingsService;
    private final CourierCertificationService certificationService;

    public AdminDispatchServiceImpl(
            AdminDispatchMapper mapper,
            OrderRepository orderRepository,
            UserRepository userRepository,
            OrderEventRepository eventRepository,
            OrderMessageService messageService,
            AdminAuditService auditService,
            PlatformSettingsService settingsService,
            CourierCertificationService certificationService) {
        this.mapper = mapper;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.messageService = messageService;
        this.auditService = auditService;
        this.settingsService = settingsService;
        this.certificationService = certificationService;
    }

    @Transactional(readOnly = true)
    @Override
    public DispatchCandidatesVO candidates(Long orderId, int requestedPage) {
        if (requestedPage < 0 || requestedPage > 100000) {
            throw new BusinessFailure(400, "页码超出范围");
        }
        CampusOrder order =
                orderRepository
                        .findById(orderId)
                        .orElseThrow(() -> new BusinessFailure(404, "订单不存在"));
        requireDispatchable(order);
        Instant now = Instant.now();
        List<DispatchCandidateVO> ranked =
                mapper
                        .findCandidates(
                                order.getServiceType().name(),
                                settingsService.get().maxActiveOrders())
                        .stream()
                        .map(
                                row -> {
                                    Instant onlineSince = row.onlineSince();
                                    Double averageRating = row.averageRating();
                                    long onlineMinutes =
                                            onlineSince == null
                                                    ? 0
                                                    : Math.max(
                                                            0,
                                                            Duration.between(onlineSince, now)
                                                                    .toMinutes());
                                    long activeOrders = row.activeOrders();
                                    long completedOrders = row.completedOrders();
                                    long matchedCompletedOrders = row.matchedCompletedOrders();
                                    long ratingCount = row.ratingCount();
                                    int score =
                                            DispatchScoreCalculator.recommendationScore(
                                                    activeOrders,
                                                    onlineMinutes,
                                                    completedOrders,
                                                    matchedCompletedOrders,
                                                    averageRating,
                                                    ratingCount);
                                    return new DispatchCandidateVO(
                                            row.id(),
                                            row.displayName(),
                                            activeOrders,
                                            onlineSince,
                                            completedOrders,
                                            matchedCompletedOrders,
                                            averageRating,
                                            ratingCount,
                                            score,
                                            false,
                                            recommendationReasons(
                                                    activeOrders,
                                                    onlineMinutes,
                                                    completedOrders,
                                                    matchedCompletedOrders,
                                                    averageRating,
                                                    ratingCount));
                                })
                        .collect(Collectors.toCollection(ArrayList::new));
        ranked.sort(
                Comparator.comparingInt(DispatchCandidateVO::recommendationScore)
                        .reversed()
                        .thenComparingLong(DispatchCandidateVO::activeOrders)
                        .thenComparing(
                                DispatchCandidateVO::onlineSince,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(DispatchCandidateVO::courierId));
        if (!ranked.isEmpty()) {
            DispatchCandidateVO first = ranked.get(0);
            ranked.set(
                    0,
                    new DispatchCandidateVO(
                            first.courierId(),
                            first.displayName(),
                            first.activeOrders(),
                            first.onlineSince(),
                            first.completedOrders(),
                            first.matchedCompletedOrders(),
                            first.averageRating(),
                            first.ratingCount(),
                            first.recommendationScore(),
                            true,
                            first.reasons()));
        }
        long total = ranked.size();
        int page =
                (int)
                        Math.min(
                                requestedPage,
                                Math.max(0, (total - 1) / AdminDispatchConstants.PAGE_SIZE));
        int from = page * AdminDispatchConstants.PAGE_SIZE;
        int to = Math.min(from + AdminDispatchConstants.PAGE_SIZE, ranked.size());
        return new DispatchCandidatesVO(
                order.getId(),
                order.getOrderNo(),
                now,
                ranked.subList(from, to),
                page,
                AdminDispatchConstants.PAGE_SIZE,
                total);
    }

    @Transactional
    @Override
    public DispatchResultVO dispatch(AppUser administrator, Long orderId, Long courierId) {
        CampusOrder order =
                orderRepository
                        .findLockedById(orderId)
                        .orElseThrow(() -> new BusinessFailure(404, "订单不存在"));
        requireDispatchable(order);
        boolean reassignment = order.getStatus() == OrderStatus.PENDING_REASSIGNMENT;
        AppUser courier =
                userRepository
                        .findLockedById(courierId)
                        .orElseThrow(() -> new BusinessFailure(404, "骑手不存在"));
        requireAvailableCourier(courier);
        long activeOrders =
                orderRepository.countByCourierAndStatusIn(
                        courier, List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP));
        if (activeOrders >= settingsService.get().maxActiveOrders()) {
            throw new BusinessFailure(409, "所选骑手已达到同时接单上限");
        }
        order.assignCourier(courier);
        eventRepository.save(new OrderEvent(order, administrator, order.getStatus()));
        messageService.send(
                order.getCustomer(),
                order,
                OrderMessageType.ORDER_ACCEPTED,
                "骑手已接单",
                "运营已安排 " + courier.getDisplayName() + " 配送，请留意订单进度");
        messageService.send(
                courier,
                order,
                OrderMessageType.ORDER_ACCEPTED,
                "运营已派单",
                "订单 " + order.getOrderNo() + " 已派给你，请尽快前往取件");
        auditService.record(
                administrator,
                reassignment
                        ? AdminAuditAction.ORDER_REASSIGNED
                        : AdminAuditAction.ORDER_DISPATCHED,
                AdminAuditTargetType.ORDER,
                order.getId(),
                "%s订单 %s 派给骑手 %s（#%d）"
                        .formatted(
                                reassignment ? "改派" : "将",
                                order.getOrderNo(),
                                courier.getDisplayName(),
                                courier.getId()));
        return new DispatchResultVO(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                courier.getId(),
                courier.getDisplayName(),
                Instant.now());
    }

    private void requireDispatchable(CampusOrder order) {
        if ((order.getStatus() != OrderStatus.PENDING_ASSIGNMENT
                        && order.getStatus() != OrderStatus.PENDING_REASSIGNMENT)
                || order.getCourier() != null) {
            throw new BusinessFailure(409, "订单已被接单，无法再次派单");
        }
        if (!order.canStartFulfillment()) {
            throw new BusinessFailure(409, "订单尚未完成支付");
        }
    }

    private void requireAvailableCourier(AppUser courier) {
        if (courier.getRole() != UserRole.COURIER) {
            throw new BusinessFailure(400, "所选用户不是骑手");
        }
        if (!courier.isEnabled()) {
            throw new BusinessFailure(409, "所选骑手账号已停用");
        }
        if (!certificationService.isEligible(courier)) {
            throw new BusinessFailure(409, "所选骑手认证未通过或已过期");
        }
        if (!courier.isCourierAcceptingOrders()
                || courier.getCourierAvailabilityUpdatedAt() == null) {
            throw new BusinessFailure(409, "所选骑手当前未上线接单");
        }
    }

    private static List<String> recommendationReasons(
            long activeOrders,
            long onlineMinutes,
            long completedOrders,
            long matchedCompletedOrders,
            Double averageRating,
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
}
