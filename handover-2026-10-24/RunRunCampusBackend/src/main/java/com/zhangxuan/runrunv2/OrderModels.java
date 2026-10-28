package com.zhangxuan.runrunv2;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public final class OrderModels {
    private OrderModels() {}
    public enum ServiceType {
        LOBBY("楼下取餐", 200, false), DOOR("送到门口", 300, false), EXPRESS("加急配送", 500, false),
        PACKAGE_SMALL("小件", 100, true), PACKAGE_MEDIUM("中件", 200, true), PACKAGE_MEDIUM_LARGE("中大件", 300, true),
        PACKAGE_LARGE("大件", 400, true), PACKAGE_EXTRA_LARGE("特大件", 500, true), PACKAGE_XL("超大件", 800, true);
        public final String name;
        public final long baseCents;
        public final boolean zoneSurcharge;
        ServiceType(String name, long cents, boolean surcharge) { this.name = name; this.baseCents = cents; this.zoneSurcharge = surcharge; }
    }
    public record Service(String code, String name, long baseCents, boolean zoneSurcharge) {}
    public record Zone(String id, String code, String name, long packageSurchargeCents, boolean enabled, long configVersion, long version) {}
    public record ServicePoint(String id, String code, String name, BigDecimal latitude, BigDecimal longitude,
                               String coordinateSystem, boolean enabled, long configVersion, long version) {}
    public record Schedule(String timezone, String open, String close) {}
    public record Config(List<Service> services, List<Zone> zones, List<ServicePoint> servicePoints,
                         Schedule businessSchedule, boolean newOrderEnabled, String reviewOwnerId, long configVersion, String id, long version) {}
    public record QuoteRequest(ServiceType serviceType, String zoneCode, String servicePointCode, String destination) {}
    @JsonIgnoreProperties(ignoreUnknown = false)
    public record CreateRequest(ServiceType serviceType, String zoneCode, String servicePointCode, String destination, Long configVersion) {
        QuoteRequest quote() { return new QuoteRequest(serviceType, zoneCode, servicePointCode, destination); }
    }
    public record Pricing(ServiceType serviceType, long baseCents, String zoneCode, long zoneSurchargeCents, long totalCents, long configVersion) {}
    public record Quote(Pricing pricingSnapshot, long configVersion) {}
    public record Address(String destination, String zoneCode, String zoneName, String servicePointCode, String servicePointName,
                          BigDecimal latitude, BigDecimal longitude, String coordinateSystem) {}
    public record Order(String id, String orderNo, int orderFlowVersion, String userId, String status, ServiceType serviceType,
                        String zoneId, String servicePointId, Address addressSnapshot, Pricing pricingSnapshot, Map<String,Object> policySnapshot,
                        long originalFeeCents, long effectiveFeeCents, OffsetDateTime pendingPaymentDeadline,
                        long version, OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime deliveredAt, OffsetDateTime confirmStartedAt, OffsetDateTime confirmDeadline) {}
    public record OriginalPayment(String id, String orderId, String purpose, long amountCents, String status, OffsetDateTime deadline, long version) {}
    public record Created(Order order, OriginalPayment originalPayment) {}
    public record ZoneChange(String code, String name, Long packageSurchargeCents, Boolean enabled) {}
    public record PointChange(String code, String name, BigDecimal latitude, BigDecimal longitude, String coordinateSystem, Boolean enabled) {}
    public record ConfigChange(Long configVersion, Long expectedVersion, Boolean newOrderEnabled, String reviewOwnerId, Schedule businessSchedule,
                               List<ZoneChange> zones, List<PointChange> servicePoints, String reason) {}
}
