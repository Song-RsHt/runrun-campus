package com.zhangxuan.runruncampus.service;

import com.zhangxuan.runruncampus.dto.CompleteOrderRequest;
import com.zhangxuan.runruncampus.dto.ConfirmPackageSizeRequest;
import com.zhangxuan.runruncampus.dto.CreateOrderRequest;
import com.zhangxuan.runruncampus.dto.CreateRatingRequest;
import com.zhangxuan.runruncampus.dto.LocationUpdateRequest;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.vo.CourierLocationResponse;
import com.zhangxuan.runruncampus.vo.CourierRatingResponse;
import com.zhangxuan.runruncampus.vo.DeliveryCodeResponse;
import com.zhangxuan.runruncampus.vo.MineOrderPage;
import com.zhangxuan.runruncampus.vo.OrderEventResponse;
import com.zhangxuan.runruncampus.vo.OrderResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
























































/**
 * 业务服务契约；由实现类维护事务与并发边界。
 */
public interface OrderService {

    OrderResponse create(AppUser customer, CreateOrderRequest request);

    OrderResponse create(AppUser customer, CreateOrderRequest request, UUID requestKey);

    List<OrderResponse> mine(AppUser user);

    List<OrderResponse> mineActive(AppUser user);

    MineOrderPage mineHistory(AppUser user, int page, int size);

    MineOrderPage mineHistory(AppUser user, int page, int size, OrderStatus status, LocalDate startDate, LocalDate endDate);

    List<OrderResponse> available(AppUser courier);

    OrderResponse get(AppUser user, Long id);

    List<OrderEventResponse> events(AppUser user, Long id);

    CourierLocationResponse location(AppUser user, Long id);

    CourierLocationResponse updateLocation(AppUser courier, Long id, LocationUpdateRequest request);

    OrderResponse accept(AppUser courier, Long id);

    OrderResponse cancel(AppUser customer, Long id);

    OrderResponse confirmPackageSize(AppUser courier, Long id, ConfirmPackageSizeRequest request);

    OrderResponse approvePackageSize(AppUser customer, Long id);

    OrderResponse rejectPackageSize(AppUser customer, Long id);

    OrderResponse markPickedUp(AppUser courier, Long id);

    DeliveryCodeResponse deliveryCode(AppUser customer, Long id);

    OrderResponse complete(AppUser courier, Long id, CompleteOrderRequest request);

    OrderResponse rate(AppUser customer, Long id, CreateRatingRequest request);

    CourierRatingResponse courierRating(AppUser courier);
}
