package com.zhangxuan.runruncampus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhangxuan.runruncampus.config.ManualQrPaymentProperties;
import com.zhangxuan.runruncampus.dto.CompleteOrderRequest;
import com.zhangxuan.runruncampus.dto.ConfirmPackageSizeRequest;
import com.zhangxuan.runruncampus.dto.CreateOrderRequest;
import com.zhangxuan.runruncampus.dto.CreateRatingRequest;
import com.zhangxuan.runruncampus.dto.LocationUpdateRequest;
import com.zhangxuan.runruncampus.entity.AppUser;
import com.zhangxuan.runruncampus.entity.CampusOrder;
import com.zhangxuan.runruncampus.entity.OrderEvent;
import com.zhangxuan.runruncampus.enums.DeliveryZone;
import com.zhangxuan.runruncampus.enums.OrderPaymentMethod;
import com.zhangxuan.runruncampus.enums.OrderPaymentStatus;
import com.zhangxuan.runruncampus.enums.OrderProofType;
import com.zhangxuan.runruncampus.enums.OrderStatus;
import com.zhangxuan.runruncampus.enums.PickupStation;
import com.zhangxuan.runruncampus.enums.ServiceType;
import com.zhangxuan.runruncampus.enums.UserRole;
import com.zhangxuan.runruncampus.enums.message.OrderMessageType;
import com.zhangxuan.runruncampus.model.CouponApplication;
import com.zhangxuan.runruncampus.model.MembershipApplication;
import com.zhangxuan.runruncampus.repository.OrderEventRepository;
import com.zhangxuan.runruncampus.repository.OrderProofRepository;
import com.zhangxuan.runruncampus.repository.OrderRepository;
import com.zhangxuan.runruncampus.repository.UserRepository;
import com.zhangxuan.runruncampus.service.api.PlatformSettingsService;
import com.zhangxuan.runruncampus.service.courier.CourierCertificationService;
import com.zhangxuan.runruncampus.service.message.OrderMessageService;
import com.zhangxuan.runruncampus.vo.CourierLocationResponse;
import com.zhangxuan.runruncampus.vo.CourierRatingResponse;
import com.zhangxuan.runruncampus.vo.DeliveryCodeResponse;
import com.zhangxuan.runruncampus.vo.MineOrderPage;
import com.zhangxuan.runruncampus.vo.OrderEventResponse;
import com.zhangxuan.runruncampus.vo.OrderResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
