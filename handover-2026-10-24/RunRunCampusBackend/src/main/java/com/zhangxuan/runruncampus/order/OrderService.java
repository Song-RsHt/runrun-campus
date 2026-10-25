package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.message.OrderMessageService;
import com.zhangxuan.runruncampus.message.OrderMessageType;
import com.zhangxuan.runruncampus.coupon.CouponService;
import com.zhangxuan.runruncampus.membership.MembershipService;
import com.zhangxuan.runruncampus.payment.PaymentService;
import com.zhangxuan.runruncampus.payment.ManualQrPaymentProperties;
import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRole;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(OrderStatus.PENDING_ASSIGNMENT,
            OrderStatus.PENDING_REASSIGNMENT, OrderStatus.ACCEPTED, OrderStatus.PICKED_UP);
    private static final List<OrderStatus> HISTORY_STATUSES = List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);
    private final OrderRepository orderRepository;
    private final OrderEventRepository eventRepository;
    private final OrderProofRepository proofRepository;
    private final OrderMessageService messageService;
    private final CouponService couponService;
    private final MembershipService membershipService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;
    private final PlatformSettingsService settingsService;
    private final CourierCertificationService certificationService;
    private final ManualQrPaymentProperties manualQrPaymentProperties;

    public OrderService(OrderRepository orderRepository, OrderEventRepository eventRepository,
                        OrderProofRepository proofRepository, OrderMessageService messageService,
                        CouponService couponService, MembershipService membershipService,
                        UserRepository userRepository, ObjectMapper objectMapper, PaymentService paymentService,
                        PlatformSettingsService settingsService, CourierCertificationService certificationService,
                        ManualQrPaymentProperties manualQrPaymentProperties) {
        this.orderRepository = orderRepository;
        this.eventRepository = eventRepository;
        this.proofRepository = proofRepository;
        this.messageService = messageService;
        this.couponService = couponService;
        this.membershipService = membershipService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.paymentService = paymentService;
        this.settingsService = settingsService;
        this.certificationService = certificationService;
        this.manualQrPaymentProperties = manualQrPaymentProperties;
    }

    @Transactional
    public OrderController.OrderResponse create(AppUser customer, OrderController.CreateOrderRequest request) {
        return create(customer, request, null);
    }

    @Transactional
    public OrderController.OrderResponse create(AppUser customer, OrderController.CreateOrderRequest request,
                                                UUID requestKey) {
        requireRole(customer, UserRole.CUSTOMER);
        String fingerprint = null;
        if (requestKey != null) {
            userRepository.findLockedById(customer.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
            fingerprint = requestFingerprint(request);
            CampusOrder existing = orderRepository.findByCustomerAndRequestKey(customer, requestKey).orElse(null);
            if (existing != null) {
                if (!fingerprint.equals(existing.getRequestFingerprint())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "提交标识已用于其他下单内容");
                }
                return response(existing);
            }
        }
        if (request.useMembership() && request.couponId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月卡与优惠券不能同时使用");
        }
        if (request.useMembership() && request.serviceType() == ServiceType.PACKAGE_SMALL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小件快递不支持使用月卡");
        }
        PickupStation pickupStation = request.pickupStation() == null
                ? PickupStation.fromLegacy(request.serviceType()) : request.pickupStation();
        DeliveryZone deliveryZone = request.deliveryZone() == null
                ? DeliveryZone.fromLegacy(request.destination()) : request.deliveryZone();
        if (!pickupStation.supports(request.serviceType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "取件点不支持当前服务方式");
        }
        int originalFeeCents = request.serviceType().getFeeCents(deliveryZone);
        MembershipService.MembershipApplication membership = request.useMembership()
                ? membershipService.apply(customer, true)
                : new MembershipService.MembershipApplication(false, null);
        CouponService.CouponApplication coupon = membership.used()
                ? new CouponService.CouponApplication(0, null)
                : couponService.apply(customer, request.couponId(), originalFeeCents);
        int discountCents = membership.used()
                ? Math.min(originalFeeCents, 200)
                : coupon.discountCents();
        CampusOrder order = new CampusOrder(nextOrderNo(), customer, request.merchant(), request.pickupCode(),
                pickupStation.getDisplayName(), request.destination(), request.serviceType(), originalFeeCents,
                discountCents, coupon.couponTitle(), coupon.couponId(), membership.used(), membership.planTitle(),
                membership.membershipId(), pickupStation, deliveryZone);
        if (manualQrPaymentProperties.enabled()) {
            order.requireManualQrPayment();
        } else {
            order.requireCashOnDelivery();
        }
        order.identifyRequest(requestKey, fingerprint);
        orderRepository.save(order);
        eventRepository.save(new OrderEvent(order, customer, order.getStatus()));
        return response(order);
    }

    @Transactional(readOnly = true)
    public List<OrderController.OrderResponse> mine(AppUser user) {
        List<CampusOrder> orders = user.getRole() == UserRole.COURIER
                ? orderRepository.findByCourierOrderByCreatedAtDesc(user)
                : orderRepository.findByCustomerOrderByCreatedAtDesc(user);
        return orders.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderController.OrderResponse> mineActive(AppUser user) {
        requireOrderOwner(user);
        List<CampusOrder> orders = user.getRole() == UserRole.COURIER
                ? orderRepository.findByCourierAndStatusInOrderByCreatedAtDesc(user, ACTIVE_STATUSES)
                : orderRepository.findByCustomerAndStatusInOrderByCreatedAtDesc(user, ACTIVE_STATUSES);
        return orders.stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public OrderController.MineOrderPage mineHistory(AppUser user, int page, int size) {
        requireOrderOwner(user);
        if (page < 0 || page > 100000 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "历史订单页码或每页数量超出范围");
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<CampusOrder> orders = user.getRole() == UserRole.COURIER
                ? orderRepository.findByCourierAndStatusIn(user, HISTORY_STATUSES, pageable)
                : orderRepository.findByCustomerAndStatusIn(user, HISTORY_STATUSES, pageable);
        return new OrderController.MineOrderPage(orders.getContent().stream().map(this::response).toList(),
                page, size, orders.getTotalElements(), orders.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrderController.MineOrderPage mineHistory(AppUser user, int page, int size, OrderStatus status,
                                                     LocalDate startDate, LocalDate endDate) {
        if (status == null && startDate == null && endDate == null) {
            return mineHistory(user, page, size);
        }
        requireOrderOwner(user);
        if (page < 0 || page > 100000 || size < 1 || size > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "历史订单页码或每页数量超出范围");
        }
        if (status != null && !HISTORY_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "历史订单仅支持已完成或已取消状态");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始日期不能晚于结束日期");
        }
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        boolean hasStart = startDate != null;
        boolean hasEnd = endDate != null;
        Instant startInclusive = hasStart ? startDate.atStartOfDay(zone).toInstant() : Instant.EPOCH;
        Instant endExclusive = hasEnd ? endDate.plusDays(1).atStartOfDay(zone).toInstant() : Instant.EPOCH;
        List<OrderStatus> statuses = status == null ? HISTORY_STATUSES : List.of(status);
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<CampusOrder> orders = user.getRole() == UserRole.COURIER
                ? orderRepository.findHistoryByCourier(user, statuses, hasStart, hasEnd,
                startInclusive, endExclusive, pageable)
                : orderRepository.findHistoryByCustomer(user, statuses, hasStart, hasEnd,
                startInclusive, endExclusive, pageable);
        return new OrderController.MineOrderPage(orders.getContent().stream().map(this::response).toList(),
                page, size, orders.getTotalElements(), orders.getTotalPages());
    }

    private void requireOrderOwner(AppUser user) {
        if (user.getRole() != UserRole.CUSTOMER && user.getRole() != UserRole.COURIER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有订单记录权限");
        }
    }

    @Transactional(readOnly = true)
    public List<OrderController.OrderResponse> available(AppUser courier) {
        requireApprovedCourier(courier);
        return orderRepository.findFulfillablePendingOrders()
                .stream().map(order -> response(order, false)).toList();
    }

    @Transactional(readOnly = true)
    public OrderController.OrderResponse get(AppUser user, Long id) {
        CampusOrder order = find(id);
        assertParticipant(user, order);
        return response(order);
    }

    @Transactional(readOnly = true)
    public List<OrderController.OrderEventResponse> events(AppUser user, Long id) {
        CampusOrder order = find(id);
        assertParticipant(user, order);
        return eventRepository.findByOrderOrderByCreatedAtAsc(order).stream()
                .map(event -> new OrderController.OrderEventResponse(
                        event.getId(), event.getStatus(), event.getActor().getDisplayName(), event.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderController.CourierLocationResponse location(AppUser user, Long id) {
        CampusOrder order = find(id);
        assertParticipant(user, order);
        return locationResponse(order);
    }

    @Transactional
    public OrderController.CourierLocationResponse updateLocation(AppUser courier, Long id,
                                                                   OrderController.LocationUpdateRequest request) {
        CampusOrder order = requireActiveCourierOrder(courier, id);
        order.updateCourierLocation(request.latitude(), request.longitude());
        return locationResponse(order);
    }

    @Transactional
    public OrderController.OrderResponse accept(AppUser courier, Long id) {
        requireApprovedCourier(courier);
        if (!courier.isCourierAcceptingOrders()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先在骑手工作台上线接单");
        }
        CampusOrder order = findLocked(id);
        if (order.getStatus() != OrderStatus.PENDING_ASSIGNMENT || order.getCourier() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已被其他骑手接走");
        }
        if (!order.canStartFulfillment()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单尚未完成支付");
        }
        AppUser lockedCourier = userRepository.findLockedById(courier.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "骑手不存在"));
        requireApprovedCourier(lockedCourier);
        if (!lockedCourier.isCourierAcceptingOrders()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先在骑手工作台上线接单");
        }
        long active = orderRepository.countByCourierAndStatusIn(lockedCourier,
                List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP));
        if (active >= settingsService.get().maxActiveOrders()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已达到同时接单上限");
        }
        order.assignCourier(lockedCourier);
        eventRepository.save(new OrderEvent(order, lockedCourier, order.getStatus()));
        messageService.send(order.getCustomer(), order, OrderMessageType.ORDER_ACCEPTED,
                "骑手已接单", lockedCourier.getDisplayName() + " 已接单，正在前往取餐点");
        return response(order);
    }

    @Transactional
    public OrderController.OrderResponse cancel(AppUser customer, Long id) {
        requireRole(customer, UserRole.CUSTOMER);
        CampusOrder order = findLocked(id);
        if (!customer.getId().equals(order.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能取消自己发布的订单");
        }
        if (order.getStatus() != OrderStatus.PENDING_ASSIGNMENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已被接单，无法取消");
        }
        paymentService.closeAndRefundForCancellation(order);
        refundBenefit(customer, order);
        order.updateStatus(OrderStatus.CANCELLED);
        eventRepository.save(new OrderEvent(order, customer, order.getStatus()));
        return response(order);
    }

    @Transactional
    public OrderController.OrderResponse confirmPackageSize(
            AppUser courier, Long id, OrderController.ConfirmPackageSizeRequest request) {
        CampusOrder order = requireCourierOrder(courier, id, OrderStatus.ACCEPTED);
        if (!order.getServiceType().isPackageDelivery() || !request.serviceType().isPackageDelivery()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能确认小件、中件、大件或超大件快递");
        }
        if (order.isPackageSizeConfirmed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "包裹规格已经确认");
        }
        if (order.getProposedPackageServiceType() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "正在等待用户确认规格调整");
        }
        if (order.isMembershipUsed() && request.serviceType() == ServiceType.PACKAGE_SMALL
                && order.getServiceType() != ServiceType.PACKAGE_SMALL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月卡订单不能调整为小件，请用户取消后重新下单");
        }
        if (request.serviceType().getFeeCents() > order.getServiceType().getFeeCents()
                && !proofRepository.existsByOrderAndType(order, OrderProofType.SIZE_REVIEW)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "提高包裹规格前请先上传规格核验照片");
        }
        if (order.getServiceType() == request.serviceType()) {
            order.confirmPackageSize(request.serviceType());
        } else {
            order.requestPackageSizeChange(request.serviceType());
            messageService.send(order.getCustomer(), order, OrderMessageType.PACKAGE_SIZE_CHANGE_REQUESTED,
                    "包裹规格待确认",
                    "骑手建议将包裹规格从%s调整为%s，请确认后再继续取件。".formatted(
                            packageSizeLabel(order.getServiceType()), packageSizeLabel(request.serviceType())));
        }
        return response(order);
    }

    @Transactional
    public OrderController.OrderResponse approvePackageSize(AppUser customer, Long id) {
        CampusOrder order = requireCustomerPackageSizeRequest(customer, id);
        ServiceType proposedServiceType = order.getProposedPackageServiceType();
        int paidBeforeChange = order.getPaidCents();
        order.confirmPackageSize(proposedServiceType);
        if (paidBeforeChange > order.getFeeCents()) {
            paymentService.refundPriceDifference(order, paidBeforeChange - order.getFeeCents());
        }
        messageService.send(order.getCourier(), order, OrderMessageType.PACKAGE_SIZE_CHANGE_APPROVED,
                "规格调整已通过",
                "用户已同意将包裹规格调整为%s，可以继续取件。".formatted(packageSizeLabel(proposedServiceType)));
        return response(order);
    }

    @Transactional
    public OrderController.OrderResponse rejectPackageSize(AppUser customer, Long id) {
        CampusOrder order = requireCustomerPackageSizeRequest(customer, id);
        ServiceType proposedServiceType = order.getProposedPackageServiceType();
        paymentService.closeAndRefundForCancellation(order);
        refundBenefit(customer, order);
        order.cancelAfterPackageSizeRejection();
        eventRepository.save(new OrderEvent(order, customer, order.getStatus()));
        messageService.send(order.getCourier(), order, OrderMessageType.PACKAGE_SIZE_CHANGE_REJECTED,
                "规格调整被拒绝，订单已取消",
                "用户未同意将包裹规格调整为%s，订单已取消。".formatted(packageSizeLabel(proposedServiceType)));
        return response(order);
    }

    private void refundBenefit(AppUser customer, CampusOrder order) {
        if (order.isMembershipUsed()) {
            membershipService.refund(customer, order.getMembershipId());
        } else if (order.getCouponId() != null) {
            couponService.refund(customer, order.getCouponId());
        }
    }

    @Transactional
    public OrderController.OrderResponse markPickedUp(AppUser courier, Long id) {
        CampusOrder order = requireCourierOrder(courier, id, OrderStatus.ACCEPTED);
        if (!order.canStartFulfillment()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单补差价支付完成后才能取件");
        }
        if (order.getServiceType().isPackageDelivery() && !order.isPackageSizeConfirmed()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先完成包裹规格确认");
        }
        if (!proofRepository.existsByOrderAndType(order, OrderProofType.PICKUP)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先上传取餐凭证");
        }
        order.updateStatus(OrderStatus.PICKED_UP);
        eventRepository.save(new OrderEvent(order, courier, order.getStatus()));
        messageService.send(order.getCustomer(), order, OrderMessageType.ORDER_PICKED_UP,
                "骑手已取餐", courier.getDisplayName() + " 已取到餐品，正在配送中");
        return response(order);
    }

    @Transactional(readOnly = true)
    public OrderController.DeliveryCodeResponse deliveryCode(AppUser customer, Long id) {
        requireRole(customer, UserRole.CUSTOMER);
        CampusOrder order = find(id);
        if (!customer.getId().equals(order.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能查看自己订单的收货码");
        }
        if (order.getStatus() != OrderStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "骑手取件后才能查看收货码");
        }
        if (order.getPaymentMethod() == OrderPaymentMethod.PERSONAL_QR_MANUAL
                && order.getPaymentStatus() != OrderPaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "管理员确认到账后才能查看收货码");
        }
        return new OrderController.DeliveryCodeResponse(order.getDeliveryCode());
    }

    @Transactional
    public OrderController.OrderResponse complete(AppUser courier, Long id,
                                                   OrderController.CompleteOrderRequest request) {
        CampusOrder order = requireCourierOrder(courier, id, OrderStatus.PICKED_UP);
        if (!proofRepository.existsByOrderAndType(order, OrderProofType.DELIVERY)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先上传送达凭证");
        }
        if (order.getPaymentMethod() == OrderPaymentMethod.PERSONAL_QR_MANUAL
                && order.getPaymentStatus() != OrderPaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "管理员尚未确认到账，暂不能完成订单");
        }
        if (!order.getDeliveryCode().equals(request.deliveryCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收货码不正确");
        }
        order.settleCashOnDelivery();
        order.updateStatus(OrderStatus.COMPLETED);
        order.markCourierSettlementPending();
        eventRepository.save(new OrderEvent(order, courier, order.getStatus()));
        messageService.send(order.getCustomer(), order, OrderMessageType.ORDER_COMPLETED,
                "订单已送达", courier.getDisplayName() + " 已确认送达，请及时取餐");
        return response(order);
    }

    @Transactional
    public OrderController.OrderResponse rate(AppUser customer, Long id,
                                               OrderController.CreateRatingRequest request) {
        requireRole(customer, UserRole.CUSTOMER);
        CampusOrder order = findLocked(id);
        if (!customer.getId().equals(order.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能评价自己发布的订单");
        }
        if (order.getStatus() != OrderStatus.COMPLETED || order.getCourier() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单完成后才能评价骑手");
        }
        if (order.getRatingScore() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单已经评价");
        }
        String comment = request.comment() == null || request.comment().isBlank()
                ? null
                : request.comment().trim();
        order.rate(request.score(), comment);
        return response(order);
    }

    @Transactional(readOnly = true)
    public OrderController.CourierRatingResponse courierRating(AppUser courier) {
        requireRole(courier, UserRole.COURIER);
        Double average = orderRepository.averageRatingForCourier(courier);
        double roundedAverage = average == null ? 0 : Math.round(average * 10.0) / 10.0;
        return new OrderController.CourierRatingResponse(
                roundedAverage, orderRepository.countByCourierAndRatingScoreIsNotNull(courier));
    }

    private CampusOrder requireCourierOrder(AppUser courier, Long id, OrderStatus expectedStatus) {
        requireRole(courier, UserRole.COURIER);
        CampusOrder order = findLocked(id);
        if (!courier.getId().equals(order.getCourier() == null ? null : order.getCourier().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该订单不属于当前骑手");
        }
        if (order.getStatus() != expectedStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单当前状态不能执行此操作");
        }
        return order;
    }

    private CampusOrder requireActiveCourierOrder(AppUser courier, Long id) {
        requireRole(courier, UserRole.COURIER);
        CampusOrder order = findLocked(id);
        if (!courier.getId().equals(order.getCourier() == null ? null : order.getCourier().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该订单不属于当前骑手");
        }
        if (order.getStatus() != OrderStatus.ACCEPTED && order.getStatus() != OrderStatus.PICKED_UP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单当前状态不能更新位置");
        }
        return order;
    }

    private CampusOrder requireCustomerPackageSizeRequest(AppUser customer, Long id) {
        requireRole(customer, UserRole.CUSTOMER);
        CampusOrder order = findLocked(id);
        if (!customer.getId().equals(order.getCustomer().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能处理自己订单的规格调整");
        }
        if (order.getStatus() != OrderStatus.ACCEPTED || order.getProposedPackageServiceType() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前没有待确认的规格调整");
        }
        return order;
    }

    private String packageSizeLabel(ServiceType serviceType) {
        return switch (serviceType) {
            case PACKAGE_SMALL -> "小件";
            case PACKAGE_MEDIUM -> "中件";
            case PACKAGE_MEDIUM_LARGE -> "中大件";
            case PACKAGE_LARGE -> "大件";
            case PACKAGE_EXTRA_LARGE -> "特大件";
            case PACKAGE_XL -> "超大件";
            default -> throw new IllegalArgumentException("不是包裹规格类型");
        };
    }

    private CampusOrder find(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
    }

    private CampusOrder findLocked(Long id) {
        return orderRepository.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在"));
    }

    private void assertParticipant(AppUser user, CampusOrder order) {
        boolean isCustomer = user.getId().equals(order.getCustomer().getId());
        boolean isCourier = order.getCourier() != null && user.getId().equals(order.getCourier().getId());
        if (!isCustomer && !isCourier) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看此订单");
        }
    }

    private void requireRole(AppUser user, UserRole role) {
        if (user.getRole() != role) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有此操作权限");
        }
    }

    private void requireApprovedCourier(AppUser courier) {
        requireRole(courier, UserRole.COURIER);
        if (!certificationService.isEligible(courier)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "骑手认证未通过或已过期，不能接单");
        }
    }

    private String nextOrderNo() {
        return "RP-" + String.format(java.util.Locale.ROOT, "%020d", orderRepository.nextOrderNumber());
    }

    private String requestFingerprint(OrderController.CreateOrderRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.valueToTree(request).toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", exception);
        }
    }

    private OrderController.OrderResponse response(CampusOrder order) {
        return response(order, true);
    }

    private OrderController.OrderResponse response(CampusOrder order, boolean includePickupCode) {
        String courierName = order.getCourier() == null ? null : order.getCourier().getDisplayName();
        boolean packageSizeReviewProofUploaded = proofRepository.existsByOrderAndType(order, OrderProofType.SIZE_REVIEW);
        boolean pickupProofUploaded = proofRepository.existsByOrderAndType(order, OrderProofType.PICKUP);
        boolean deliveryProofUploaded = proofRepository.existsByOrderAndType(order, OrderProofType.DELIVERY);
        return new OrderController.OrderResponse(order.getId(), order.getOrderNo(), order.getMerchant(),
                includePickupCode ? order.getPickupCode() : null,
                order.getPickupLocation(), order.getDestination(), order.getPickupStation(), order.getDeliveryZone(),
                order.getServiceType(), order.getStatus(),
                order.getOriginalFeeCents(), order.getDiscountCents(), order.getFeeCents(),
                order.getPaymentMethod(), order.getPaymentStatus(), order.getPaidCents(),
                order.getManualPaymentStatus(), order.getPayerReference(), order.getPaymentRequestedAt(),
                order.getPaymentSubmittedAt(), order.getPaymentReviewedAt(), order.getPaymentReviewNote(),
                order.getCourierSettlementStatus(), order.getCourierSettledAt(),
                order.getCouponTitle(),
                order.isMembershipUsed(), order.getMembershipPlanTitle(),
                courierName, packageSizeReviewProofUploaded, pickupProofUploaded, deliveryProofUploaded,
                order.isPackageSizeConfirmed(), order.getProposedPackageServiceType(),
                order.getRatingScore(), order.getRatingComment(), order.getRatedAt(),
                java.util.Optional.ofNullable(paymentService.refundsForOrder(order)).orElse(List.of()),
                order.getCreatedAt(), order.getUpdatedAt());
    }

    private OrderController.CourierLocationResponse locationResponse(CampusOrder order) {
        return new OrderController.CourierLocationResponse(order.getCourierLatitude() != null,
                order.getCourierLatitude(), order.getCourierLongitude(), order.getCourierLocationUpdatedAt());
    }
}
