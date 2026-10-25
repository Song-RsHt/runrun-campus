package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.courier.CourierCertification;
import com.zhangxuan.runruncampus.courier.CourierCertificationRepository;
import com.zhangxuan.runruncampus.courier.CourierCertificationStatus;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdminCourierService {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private final UserRepository userRepository;
    private final CourierCertificationRepository certificationRepository;
    private final OrderRepository orderRepository;

    public AdminCourierService(UserRepository userRepository,
                               CourierCertificationRepository certificationRepository,
                               OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.certificationRepository = certificationRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public AdminCourierController.AdminCourierPageResponse list(
            String query, CourierCertificationStatus status, int page, int size) {
        String normalizedQuery = normalize(query);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<AppUser> couriers = normalizedQuery == null
                ? userRepository.filterCouriersForAdmin(status, pageable)
                : userRepository.searchCouriersForAdmin(normalizedQuery, status, pageable);
        Map<Long, CourierCertification> certifications = couriers.isEmpty()
                ? Map.of()
                : certificationRepository.findByCourierIn(couriers.getContent()).stream()
                        .collect(Collectors.toMap(item -> item.getCourier().getId(), Function.identity()));
        Map<Long, OrderRepository.CourierCompletedOrderStats> completedOrderStats = couriers.isEmpty()
                ? Map.of()
                : orderRepository.countCompletedOrdersByCourierIds(
                                couriers.getContent().stream().map(AppUser::getId).toList()).stream()
                        .collect(Collectors.toMap(OrderRepository.CourierCompletedOrderStats::getCourierId,
                                Function.identity()));
        return new AdminCourierController.AdminCourierPageResponse(
                couriers.getContent().stream()
                        .map(courier -> {
                            OrderRepository.CourierCompletedOrderStats stats = completedOrderStats.get(courier.getId());
                            return item(courier, certifications.get(courier.getId()),
                                    stats == null ? 0L : stats.getCompletedOrderCount(),
                                    stats == null ? 0L : stats.getCompletedPackageOrderCount());
                        })
                        .toList(),
                couriers.getNumber(), couriers.getSize(), couriers.getTotalElements(), couriers.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminCourierController.AdminCourierPerformanceResponse performance(Long courierId, int page, int size) {
        AppUser courier = userRepository.findById(courierId)
                .filter(user -> user.getRole() == UserRole.COURIER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "骑手不存在"));
        LocalDate today = LocalDate.now(CAMPUS_ZONE);
        Instant todayStart = today.atStartOfDay(CAMPUS_ZONE).toInstant();
        Instant lastSevenDaysStart = today.minusDays(6).atStartOfDay(CAMPUS_ZONE).toInstant();
        Instant currentMonthStart = today.withDayOfMonth(1).atStartOfDay(CAMPUS_ZONE).toInstant();
        OrderRepository.CourierPackagePerformanceStats stats = orderRepository.packagePerformanceForCourier(
                courier.getId(), todayStart, lastSevenDaysStart, currentMonthStart);
        Page<CampusOrder> orders = orderRepository.findPackageOrdersForCourier(
                courier.getId(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new AdminCourierController.AdminCourierPerformanceResponse(
                value(stats == null ? null : stats.getTodayCompletedPackageOrders()),
                value(stats == null ? null : stats.getLastSevenDaysCompletedPackageOrders()),
                value(stats == null ? null : stats.getCurrentMonthCompletedPackageOrders()),
                value(stats == null ? null : stats.getTotalCompletedPackageOrders()),
                value(stats == null ? null : stats.getSmallCompletedPackageOrders()),
                value(stats == null ? null : stats.getMediumCompletedPackageOrders()),
                value(stats == null ? null : stats.getMediumLargeCompletedPackageOrders()),
                value(stats == null ? null : stats.getLargeCompletedPackageOrders()),
                value(stats == null ? null : stats.getSpecialLargeCompletedPackageOrders()),
                value(stats == null ? null : stats.getExtraLargeCompletedPackageOrders()),
                new AdminCourierController.AdminCourierOrderPage(
                        orders.getContent().stream().map(this::orderItem).toList(),
                        orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages()));
    }

    private AdminCourierController.AdminCourierItem item(
            AppUser courier, CourierCertification certification, long completedOrderCount,
            long completedPackageOrderCount) {
        if (certification == null) {
            CourierCertificationStatus status = courier.isCourierApproved()
                    ? CourierCertificationStatus.APPROVED
                    : CourierCertificationStatus.NOT_APPLIED;
            return new AdminCourierController.AdminCourierItem(
                    courier.getId(), courier.getDisplayName(), courier.getPhone(), courier.isCourierApproved(),
                    courier.getCreatedAt(), completedOrderCount, completedPackageOrderCount,
                    null, null, null, null, status, null, null, null, null, null, null, false);
        }
        AppUser reviewer = certification.getReviewer();
        return new AdminCourierController.AdminCourierItem(
                courier.getId(), courier.getDisplayName(), courier.getPhone(),
                courier.isCourierApproved(),
                courier.getCreatedAt(), completedOrderCount, completedPackageOrderCount,
                certification.getId(), blankToNull(certification.getRealName()),
                blankToNull(certification.getStudentId()), blankToNull(certification.getCampus()), certification.getStatus(),
                certification.getRejectionReason(), certification.getSubmittedAt(), certification.getReviewedAt(),
                reviewer == null ? null : reviewer.getDisplayName(),
                null, null, certification.hasCurrentPhoto());
    }

    private AdminCourierController.AdminCourierOrderItem orderItem(CampusOrder order) {
        return new AdminCourierController.AdminCourierOrderItem(
                order.getId(), order.getOrderNo(), order.getStatus(), order.getServiceType(),
                order.getDestination(), order.getFeeCents(), order.getCustomer().getDisplayName(),
                order.getCreatedAt(), order.getUpdatedAt());
    }

    private long value(Long number) {
        return number == null ? 0L : number;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String normalize(String query) {
        if (query == null || query.isBlank()) return null;
        return query.trim();
    }
}
