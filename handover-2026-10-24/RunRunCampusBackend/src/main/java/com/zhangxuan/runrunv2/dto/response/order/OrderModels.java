package com.zhangxuan.runrunv2.dto.response.order;

import com.zhangxuan.runrunv2.enums.order.ServiceType;
import com.zhangxuan.runrunv2.model.order.Address;
import com.zhangxuan.runrunv2.model.order.Pricing;
import com.zhangxuan.runrunv2.model.order.Schedule;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class OrderModels {

  private OrderModels() {}

  public record Service(String code, String name, long baseCents, boolean zoneSurcharge) {}

  public record Zone(
      String id,
      String code,
      String name,
      long packageSurchargeCents,
      boolean enabled,
      long configVersion,
      long version) {}

  public record ServicePoint(
      String id,
      String code,
      String name,
      BigDecimal latitude,
      BigDecimal longitude,
      String coordinateSystem,
      boolean enabled,
      long configVersion,
      long version) {}

  public record Config(
      List<Service> services,
      List<Zone> zones,
      List<ServicePoint> servicePoints,
      Schedule businessSchedule,
      boolean newOrderEnabled,
      String reviewOwnerId,
      long configVersion,
      String id,
      long version) {}

  public record Quote(Pricing pricingSnapshot, long configVersion) {}

  public record Order(
      String id,
      String orderNo,
      int orderFlowVersion,
      String userId,
      String status,
      ServiceType serviceType,
      String zoneId,
      String servicePointId,
      Address addressSnapshot,
      Pricing pricingSnapshot,
      Map<String, Object> policySnapshot,
      long originalFeeCents,
      long effectiveFeeCents,
      OffsetDateTime pendingPaymentDeadline,
      long version,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      OffsetDateTime deliveredAt,
      OffsetDateTime confirmStartedAt,
      OffsetDateTime confirmDeadline) {}

  public record OriginalPayment(
      String id,
      String orderId,
      String purpose,
      long amountCents,
      String status,
      OffsetDateTime deadline,
      long version) {}

  public record Created(Order order, OriginalPayment originalPayment) {}
}
