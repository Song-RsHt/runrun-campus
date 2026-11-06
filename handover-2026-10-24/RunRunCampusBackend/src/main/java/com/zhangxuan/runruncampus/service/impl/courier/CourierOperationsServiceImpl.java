package com.zhangxuan.runruncampus.service.impl.courier;

import com.zhangxuan.runruncampus.entity.user.AppUser;
import com.zhangxuan.runruncampus.enums.order.OrderStatus;
import com.zhangxuan.runruncampus.enums.user.UserRole;
import com.zhangxuan.runruncampus.exception.BusinessFailure;
import com.zhangxuan.runruncampus.model.order.CourierBusinessStats;
import com.zhangxuan.runruncampus.repository.order.OrderRepository;
import com.zhangxuan.runruncampus.repository.user.UserRepository;
import com.zhangxuan.runruncampus.service.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.service.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.service.courier.CourierOperationsService;
import com.zhangxuan.runruncampus.vo.courier.OperationsResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** 骑手接单开关业务实现；通过注入的业务契约及数据访问组件执行，事务边界保留在本类。 */
@Service
public class CourierOperationsServiceImpl implements CourierOperationsService {

    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<OrderStatus> ACTIVE_STATUSES =
            List.of(OrderStatus.ACCEPTED, OrderStatus.PICKED_UP);

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final CourierCertificationService certificationService;
    private final PlatformSettingsService settingsService;

    public CourierOperationsServiceImpl(
            UserRepository userRepository,
            OrderRepository orderRepository,
            CourierCertificationService certificationService,
            PlatformSettingsService settingsService) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.certificationService = certificationService;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    @Override
    public OperationsResponse overview(AppUser courier) {
        requireCourier(courier);
        return response(courier);
    }

    @Transactional
    @Override
    public OperationsResponse updateAvailability(AppUser courier, boolean acceptingOrders) {
        requireCourier(courier);
        AppUser locked =
                userRepository
                        .findLockedById(courier.getId())
                        .orElseThrow(() -> new BusinessFailure(404, "骑手不存在"));
        if (acceptingOrders && !certificationService.isEligible(locked)) {
            throw new BusinessFailure(403, "骑手认证有效后才能上线接单");
        }
        locked.setCourierAcceptingOrders(acceptingOrders);
        return response(locked);
    }

    private OperationsResponse response(AppUser courier) {
        boolean approved = certificationService.isEligible(courier);
        Instant todayStart = LocalDate.now(CAMPUS_ZONE).atStartOfDay(CAMPUS_ZONE).toInstant();
        CourierBusinessStats stats = orderRepository.businessStatsForCourier(courier, todayStart);
        Double average = orderRepository.averageRatingForCourier(courier);
        double averageRating = average == null ? 0 : Math.round(average * 10.0) / 10.0;
        return new OperationsResponse(
                approved,
                approved && courier.isCourierAcceptingOrders(),
                courier.getCourierAvailabilityUpdatedAt(),
                orderRepository.countFulfillablePendingOrders(),
                orderRepository.countByCourierAndStatusIn(courier, ACTIVE_STATUSES),
                settingsService.get().maxActiveOrders(),
                value(stats == null ? null : stats.getTodayCompletedOrders()),
                value(stats == null ? null : stats.getTotalCompletedOrders()),
                value(stats == null ? null : stats.getTodayGrossServiceFeeCents()),
                value(stats == null ? null : stats.getTotalGrossServiceFeeCents()),
                averageRating,
                orderRepository.countByCourierAndRatingScoreIsNotNull(courier),
                Instant.now());
    }

    private long value(Long number) {
        return number == null ? 0 : number;
    }

    private void requireCourier(AppUser user) {
        if (user.getRole() != UserRole.COURIER) {
            throw new BusinessFailure(403, "只有骑手可以访问经营工作台");
        }
    }
}
