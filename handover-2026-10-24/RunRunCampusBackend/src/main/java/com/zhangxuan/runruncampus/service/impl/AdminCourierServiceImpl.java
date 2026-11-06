package com.zhangxuan.runruncampus.service.impl;

import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.CourierCertification;
import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.model.CourierCompletedOrderStats;
import com.zhangxuan.runruncampus.model.CourierPackagePerformanceStats;
import com.zhangxuan.runruncampus.repository.CourierCertificationRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.vo.AdminCourierItem;
import com.zhangxuan.runruncampus.vo.AdminCourierOrderItem;
import com.zhangxuan.runruncampus.vo.AdminCourierOrderPage;
import com.zhangxuan.runruncampus.vo.AdminCourierPageResponse;
import com.zhangxuan.runruncampus.vo.AdminCourierPerformanceResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.service.AdminCourierService;

@Service
public class AdminCourierServiceImpl implements AdminCourierService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");

    private final UserRepository userRepository;

    private final CourierCertificationRepository certificationRepository;

    private final OrderRepository orderRepository;

    public AdminCourierServiceImpl(UserRepository userRepository, CourierCertificationRepository certificationRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.certificationRepository = certificationRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    @Override
    public AdminCourierPageResponse list(String query, CourierCertificationStatus status, int page, int size) {
        String normalizedQuery = normalize(query);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Direction.DESC, "createdAt", "id"));
        Page<AppUser> couriers = normalizedQuery == null ? userRepository.filterCouriersForAdmin(status, pageable) : userRepository.searchCouriersForAdmin(normalizedQuery, status, pageable);
        Map<Long, CourierCertification> certifications = couriers.isEmpty() ? Map.of() : certificationRepository.findByCourierIn(couriers.getContent()).stream().collect(Collectors.toMap(item -> item.getCourier().getId(), Function.identity()));
        Map<Long, CourierCompletedOrderStats> completedOrderStats = couriers.isEmpty() ? Map.of() : orderRepository.countCompletedOrdersByCourierIds(couriers.getContent().stream().map(AppUser::getId).toList()).stream().collect(Collectors.toMap(CourierCompletedOrderStats::getCourierId, Function.identity()));
        return new AdminCourierPageResponse(couriers.getContent().stream().map(courier -> {
            CourierCompletedOrderStats stats = completedOrderStats.get(courier.getId());
            return item(courier, certifications.get(courier.getId()), stats == null ? 0L : stats.getCompletedOrderCount(), stats == null ? 0L : stats.getCompletedPackageOrderCount());
        }).toList(), couriers.getNumber(), couriers.getSize(), couriers.getTotalElements(), couriers.getTotalPages());
    }

    @Transactional(readOnly = true)
    @Override
    public AdminCourierPerformanceResponse performance(Long courierId, int page, int size) {
        AppUser courier = userRepository.findById(courierId).filter(user -> user.getRole() == UserRole.COURIER).orElseThrow(() -> new BusinessFailure(404, "骑手不存在"));
        LocalDate today = LocalDate.now(CAMPUS_ZONE);
        Instant todayStart = today.atStartOfDay(CAMPUS_ZONE).toInstant();
        Instant lastSevenDaysStart = today.minusDays(6).atStartOfDay(CAMPUS_ZONE).toInstant();
        Instant currentMonthStart = today.withDayOfMonth(1).atStartOfDay(CAMPUS_ZONE).toInstant();
        CourierPackagePerformanceStats stats = orderRepository.packagePerformanceForCourier(courier.getId(), todayStart, lastSevenDaysStart, currentMonthStart);
        Page<CampusOrder> orders = orderRepository.findPackageOrdersForCourier(courier.getId(), PageRequest.of(page, size, Sort.by(Direction.DESC, "createdAt", "id")));
        return new AdminCourierPerformanceResponse(value(stats == null ? null : stats.getTodayCompletedPackageOrders()), value(stats == null ? null : stats.getLastSevenDaysCompletedPackageOrders()), value(stats == null ? null : stats.getCurrentMonthCompletedPackageOrders()), value(stats == null ? null : stats.getTotalCompletedPackageOrders()), value(stats == null ? null : stats.getSmallCompletedPackageOrders()), value(stats == null ? null : stats.getMediumCompletedPackageOrders()), value(stats == null ? null : stats.getMediumLargeCompletedPackageOrders()), value(stats == null ? null : stats.getLargeCompletedPackageOrders()), value(stats == null ? null : stats.getSpecialLargeCompletedPackageOrders()), value(stats == null ? null : stats.getExtraLargeCompletedPackageOrders()), new AdminCourierOrderPage(orders.getContent().stream().map(this::orderItem).toList(), orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages()));
    }

    private AdminCourierItem item(AppUser courier, CourierCertification certification, long completedOrderCount, long completedPackageOrderCount) {
        if (certification == null) {
            CourierCertificationStatus status = courier.isCourierApproved() ? CourierCertificationStatus.APPROVED : CourierCertificationStatus.NOT_APPLIED;
            return new AdminCourierItem(courier.getId(), courier.getDisplayName(), courier.getPhone(), courier.isCourierApproved(), courier.getCreatedAt(), completedOrderCount, completedPackageOrderCount, null, null, null, null, status, null, null, null, null, null, null, false);
        }
        AppUser reviewer = certification.getReviewer();
        return new AdminCourierItem(courier.getId(), courier.getDisplayName(), courier.getPhone(), courier.isCourierApproved(), courier.getCreatedAt(), completedOrderCount, completedPackageOrderCount, certification.getId(), blankToNull(certification.getRealName()), blankToNull(certification.getStudentId()), blankToNull(certification.getCampus()), certification.getStatus(), certification.getRejectionReason(), certification.getSubmittedAt(), certification.getReviewedAt(), reviewer == null ? null : reviewer.getDisplayName(), null, null, certification.hasCurrentPhoto());
    }

    private AdminCourierOrderItem orderItem(CampusOrder order) {
        return new AdminCourierOrderItem(order.getId(), order.getOrderNo(), order.getStatus(), order.getServiceType(), order.getDestination(), order.getFeeCents(), order.getCustomer().getDisplayName(), order.getCreatedAt(), order.getUpdatedAt());
    }

    private long value(Long number) {
        return number == null ? 0L : number;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String normalize(String query) {
        if (query == null || query.isBlank())
            return null;
        return query.trim();
    }
}
