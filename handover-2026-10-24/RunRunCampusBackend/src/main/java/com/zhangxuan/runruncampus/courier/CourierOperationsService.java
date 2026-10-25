package com.zhangxuan.runruncampus.courier;

import com.zhangxuan.runruncampus.order.OrderRepository;
import com.zhangxuan.runruncampus.order.OrderStatus;
import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.user.UserRepository;
import com.zhangxuan.runruncampus.user.UserRole;
import com.zhangxuan.runruncampus.api.PlatformSettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class CourierOperationsService {
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP);

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CourierCertificationService certificationService;
    private final PlatformSettingsService settingsService;

    public CourierOperationsService(UserRepository userRepository, OrderRepository orderRepository,
                                    CourierCertificationService certificationService,
                                    PlatformSettingsService settingsService) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.certificationService = certificationService;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public CourierOperationsController.OperationsResponse overview(AppUser courier) {
        requireCourier(courier);
        return response(courier);
    }

    @Transactional
    public CourierOperationsController.OperationsResponse updateAvailability(AppUser courier, boolean acceptingOrders) {
        requireCourier(courier);
        AppUser locked = userRepository.findLockedById(courier.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "骑手不存在"));
        if (acceptingOrders && !certificationService.isEligible(locked)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "骑手认证有效后才能上线接单");
        }
        locked.setCourierAcceptingOrders(acceptingOrders);
        return response(locked);
    }

    private CourierOperationsController.OperationsResponse response(AppUser courier) {
        boolean approved = certificationService.isEligible(courier);
        Instant todayStart = LocalDate.now(CAMPUS_ZONE).atStartOfDay(CAMPUS_ZONE).toInstant();
        OrderRepository.CourierBusinessStats stats = orderRepository.businessStatsForCourier(courier, todayStart);
        Double average = orderRepository.averageRatingForCourier(courier);
        double averageRating = average == null ? 0 : Math.round(average * 10.0) / 10.0;
        return new CourierOperationsController.OperationsResponse(
                approved, approved && courier.isCourierAcceptingOrders(),
                courier.getCourierAvailabilityUpdatedAt(),
                orderRepository.countFulfillablePendingOrders(),
                orderRepository.countByCourierAndStatusIn(courier, ACTIVE_STATUSES),
                settingsService.get().maxActiveOrders(),
                value(stats == null ? null : stats.getTodayCompletedOrders()),
                value(stats == null ? null : stats.getTotalCompletedOrders()),
                value(stats == null ? null : stats.getTodayGrossServiceFeeCents()),
                value(stats == null ? null : stats.getTotalGrossServiceFeeCents()),
                averageRating, orderRepository.countByCourierAndRatingScoreIsNotNull(courier), Instant.now());
    }

    private long value(Long number) { return number == null ? 0 : number; }

    private void requireCourier(AppUser user) {
        if (user.getRole() != UserRole.COURIER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有骑手可以访问经营工作台");
        }
    }
}
