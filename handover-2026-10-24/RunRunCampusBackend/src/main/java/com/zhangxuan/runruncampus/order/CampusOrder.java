package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "campus_orders")
public class CampusOrder {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 24)
    private String orderNo;

    @Column(updatable = false)
    private UUID requestKey;

    @Column(length = 64, updatable = false)
    private String requestFingerprint;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser customer;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser courier;

    @Column(nullable = false, length = 80)
    private String merchant;

    @Column(nullable = false, length = 32)
    private String pickupCode;

    @Column(nullable = false, length = 6)
    private String deliveryCode;

    @Column(nullable = false, length = 120)
    private String pickupLocation;

    @Column(nullable = false, length = 120)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PickupStation pickupStation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryZone deliveryZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ServiceType serviceType;

    @Column(nullable = false)
    private boolean packageSizeConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ServiceType proposedPackageServiceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    private Instant archivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser archivedBy;

    @Column(nullable = false)
    private int feeCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderPaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderPaymentMethod paymentMethod;

    @Column(nullable = false)
    private int paidCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ManualPaymentStatus manualPaymentStatus = ManualPaymentStatus.NOT_REQUESTED;

    @Column(length = 64)
    private String payerReference;

    private Instant paymentRequestedAt;

    private Instant paymentSubmittedAt;

    private Instant paymentReviewedAt;

    @Column(length = 200)
    private String paymentReviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser paymentReviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CourierSettlementStatus courierSettlementStatus = CourierSettlementStatus.NOT_APPLICABLE;

    private Instant courierSettledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser courierSettlementReviewer;

    private Integer originalFeeCents;

    private Integer discountCents;

    @Column(length = 48)
    private String couponTitle;

    private Long couponId;

    private Boolean membershipUsed;

    @Column(length = 48)
    private String membershipPlanTitle;

    private Long membershipId;

    private Integer ratingScore;

    @Column(length = 300)
    private String ratingComment;

    private Instant ratedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Double courierLatitude;

    private Double courierLongitude;

    private Instant courierLocationUpdatedAt;

    protected CampusOrder() {
    }

    public void identifyRequest(UUID requestKey, String requestFingerprint) {
        this.requestKey = requestKey;
        this.requestFingerprint = requestFingerprint;
    }

    public String getRequestFingerprint() { return requestFingerprint; }

    public CampusOrder(String orderNo, AppUser customer, String merchant, String pickupCode, String pickupLocation,
                       String destination, ServiceType serviceType) {
        this(orderNo, customer, merchant, pickupCode, pickupLocation, destination, serviceType,
                serviceType.getFeeCents(DeliveryZone.fromLegacy(destination)), 0, null);
    }

    public CampusOrder(String orderNo, AppUser customer, String merchant, String pickupCode, String pickupLocation,
                       String destination, ServiceType serviceType, int originalFeeCents, int discountCents,
                       String couponTitle) {
        this(orderNo, customer, merchant, pickupCode, pickupLocation, destination, serviceType,
                originalFeeCents, discountCents, couponTitle, false, null);
    }

    public CampusOrder(String orderNo, AppUser customer, String merchant, String pickupCode, String pickupLocation,
                       String destination, ServiceType serviceType, int originalFeeCents, int discountCents,
                       String couponTitle, boolean membershipUsed, String membershipPlanTitle) {
        this(orderNo, customer, merchant, pickupCode, pickupLocation, destination, serviceType, originalFeeCents,
                discountCents, couponTitle, membershipUsed, membershipPlanTitle, null);
    }

    public CampusOrder(String orderNo, AppUser customer, String merchant, String pickupCode, String pickupLocation,
                       String destination, ServiceType serviceType, int originalFeeCents, int discountCents,
                       String couponTitle, boolean membershipUsed, String membershipPlanTitle, Long membershipId) {
        this(orderNo, customer, merchant, pickupCode, pickupLocation, destination, serviceType, originalFeeCents,
                discountCents, couponTitle, null, membershipUsed, membershipPlanTitle, membershipId);
    }

    public CampusOrder(String orderNo, AppUser customer, String merchant, String pickupCode, String pickupLocation,
                       String destination, ServiceType serviceType, int originalFeeCents, int discountCents,
                       String couponTitle, Long couponId, boolean membershipUsed, String membershipPlanTitle,
                       Long membershipId) {
        this(orderNo, customer, merchant, pickupCode, pickupLocation, destination, serviceType, originalFeeCents,
                discountCents, couponTitle, couponId, membershipUsed, membershipPlanTitle, membershipId,
                PickupStation.fromLegacy(serviceType), DeliveryZone.fromLegacy(destination));
    }

    public CampusOrder(String orderNo, AppUser customer, String merchant, String pickupCode, String pickupLocation,
                       String destination, ServiceType serviceType, int originalFeeCents, int discountCents,
                       String couponTitle, Long couponId, boolean membershipUsed, String membershipPlanTitle,
                       Long membershipId, PickupStation pickupStation, DeliveryZone deliveryZone) {
        this.orderNo = orderNo;
        this.customer = customer;
        this.merchant = merchant;
        this.pickupCode = pickupCode;
        this.deliveryCode = "%06d".formatted(SECURE_RANDOM.nextInt(1_000_000));
        this.pickupLocation = pickupLocation;
        this.destination = destination;
        this.pickupStation = pickupStation;
        this.deliveryZone = deliveryZone;
        this.serviceType = serviceType;
        this.packageSizeConfirmed = !serviceType.isPackageDelivery();
        this.status = OrderStatus.PENDING_ASSIGNMENT;
        this.originalFeeCents = originalFeeCents;
        this.discountCents = discountCents;
        this.feeCents = Math.max(0, originalFeeCents - discountCents);
        this.paymentStatus = OrderPaymentStatus.PAID;
        this.paymentMethod = OrderPaymentMethod.WECHAT_APP;
        this.paidCents = this.feeCents;
        this.couponTitle = couponTitle;
        this.couponId = couponId;
        this.membershipUsed = membershipUsed;
        this.membershipPlanTitle = membershipPlanTitle;
        this.membershipId = membershipId;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void assignCourier(AppUser courier) { this.courier = courier; updateStatus(OrderStatus.ACCEPTED); }
    public void requestReassignment() {
        this.courier = null;
        this.courierLatitude = null;
        this.courierLongitude = null;
        this.courierLocationUpdatedAt = null;
        this.proposedPackageServiceType = null;
        updateStatus(OrderStatus.PENDING_REASSIGNMENT);
    }
    public void updateStatus(OrderStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public void archive(AppUser administrator) {
        this.archivedAt = Instant.now();
        this.archivedBy = administrator;
        this.updatedAt = archivedAt;
    }
    public void unarchive() {
        this.archivedAt = null;
        this.archivedBy = null;
        this.updatedAt = Instant.now();
    }
    public void updateCourierLocation(double latitude, double longitude) {
        this.courierLatitude = latitude;
        this.courierLongitude = longitude;
        this.courierLocationUpdatedAt = Instant.now();
    }
    public void rate(int score, String comment) {
        this.ratingScore = score;
        this.ratingComment = comment;
        this.ratedAt = Instant.now();
        this.updatedAt = ratedAt;
    }
    public void requestPackageSizeChange(ServiceType proposedServiceType) {
        if (!serviceType.isPackageDelivery() || !proposedServiceType.isPackageDelivery()) {
            throw new IllegalArgumentException("包裹规格只能使用快递服务类型");
        }
        this.packageSizeConfirmed = false;
        this.proposedPackageServiceType = proposedServiceType;
        this.updatedAt = Instant.now();
    }
    public void confirmPackageSize(ServiceType confirmedServiceType) {
        if (!serviceType.isPackageDelivery() || !confirmedServiceType.isPackageDelivery()) {
            throw new IllegalArgumentException("包裹规格只能使用快递服务类型");
        }
        this.serviceType = confirmedServiceType;
        this.originalFeeCents = confirmedServiceType.getFeeCents(deliveryZone);
        this.discountCents = isMembershipUsed()
                ? Math.min(originalFeeCents, 200)
                : Math.min(getDiscountCents(), originalFeeCents);
        this.feeCents = Math.max(0, originalFeeCents - discountCents);
        this.paymentStatus = paidCents >= feeCents ? OrderPaymentStatus.PAID : OrderPaymentStatus.UNPAID;
        this.packageSizeConfirmed = true;
        this.proposedPackageServiceType = null;
        this.updatedAt = Instant.now();
    }
    public void cancelAfterPackageSizeRejection() {
        this.proposedPackageServiceType = null;
        this.packageSizeConfirmed = false;
        updateStatus(OrderStatus.CANCELLED);
    }

    public void requirePayment() {
        this.paymentMethod = OrderPaymentMethod.WECHAT_APP;
        this.paidCents = 0;
        this.paymentStatus = feeCents == 0 ? OrderPaymentStatus.PAID : OrderPaymentStatus.UNPAID;
    }

    public void requireCashOnDelivery() {
        this.paymentMethod = OrderPaymentMethod.CASH_ON_DELIVERY;
        this.paidCents = 0;
        this.paymentStatus = feeCents == 0 ? OrderPaymentStatus.PAID : OrderPaymentStatus.UNPAID;
    }

    public void requireManualQrPayment() {
        this.paymentMethod = OrderPaymentMethod.PERSONAL_QR_MANUAL;
        this.paidCents = 0;
        this.paymentStatus = feeCents == 0 ? OrderPaymentStatus.PAID : OrderPaymentStatus.UNPAID;
        this.manualPaymentStatus = ManualPaymentStatus.NOT_REQUESTED;
        this.courierSettlementStatus = CourierSettlementStatus.NOT_APPLICABLE;
    }

    public boolean canStartFulfillment() {
        return paymentMethod == OrderPaymentMethod.CASH_ON_DELIVERY
                || paymentMethod == OrderPaymentMethod.PERSONAL_QR_MANUAL
                || paymentStatus == OrderPaymentStatus.PAID;
    }

    public void settleCashOnDelivery() {
        if (paymentMethod != OrderPaymentMethod.CASH_ON_DELIVERY) return;
        this.paidCents = feeCents;
        this.paymentStatus = OrderPaymentStatus.PAID;
        this.updatedAt = Instant.now();
    }

    public void requestManualPayment() {
        this.manualPaymentStatus = ManualPaymentStatus.AWAITING_PAYMENT;
        this.paymentRequestedAt = Instant.now();
        this.paymentReviewNote = null;
        this.updatedAt = paymentRequestedAt;
    }

    public void submitManualPayment(String payerReference) {
        this.manualPaymentStatus = ManualPaymentStatus.SUBMITTED;
        this.payerReference = payerReference;
        this.paymentSubmittedAt = Instant.now();
        this.paymentReviewer = null;
        this.paymentReviewedAt = null;
        this.paymentReviewNote = null;
        this.updatedAt = paymentSubmittedAt;
    }

    public void confirmManualPayment(AppUser reviewer) {
        this.manualPaymentStatus = ManualPaymentStatus.CONFIRMED;
        this.paymentReviewer = reviewer;
        this.paymentReviewedAt = Instant.now();
        this.paymentReviewNote = null;
        this.paidCents = feeCents;
        this.paymentStatus = OrderPaymentStatus.PAID;
        this.updatedAt = paymentReviewedAt;
    }

    public void rejectManualPayment(AppUser reviewer, String reason) {
        this.manualPaymentStatus = ManualPaymentStatus.REJECTED;
        this.paymentReviewer = reviewer;
        this.paymentReviewedAt = Instant.now();
        this.paymentReviewNote = reason;
        this.updatedAt = paymentReviewedAt;
    }

    public void markCourierSettlementPending() {
        if (paymentMethod == OrderPaymentMethod.PERSONAL_QR_MANUAL && paymentStatus == OrderPaymentStatus.PAID) {
            this.courierSettlementStatus = CourierSettlementStatus.PENDING;
            this.updatedAt = Instant.now();
        }
    }

    public void confirmCourierSettlement(AppUser reviewer) {
        this.courierSettlementStatus = CourierSettlementStatus.SETTLED;
        this.courierSettlementReviewer = reviewer;
        this.courierSettledAt = Instant.now();
        this.updatedAt = courierSettledAt;
    }

    public void recordPayment(int amountCents) {
        if (amountCents <= 0) throw new IllegalArgumentException("支付金额必须大于 0");
        this.paidCents += amountCents;
        this.paymentStatus = paidCents >= feeCents ? OrderPaymentStatus.PAID : OrderPaymentStatus.UNPAID;
        this.updatedAt = Instant.now();
    }

    public void recordRefundScheduled(int amountCents, boolean cancellation) {
        if (amountCents <= 0 || amountCents > paidCents) throw new IllegalArgumentException("退款金额不正确");
        this.paidCents -= amountCents;
        if (cancellation) this.paymentStatus = OrderPaymentStatus.REFUNDING;
        this.updatedAt = Instant.now();
    }

    public void markRefunded() {
        if (status == OrderStatus.CANCELLED && paidCents == 0) paymentStatus = OrderPaymentStatus.REFUNDED;
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public AppUser getCustomer() { return customer; }
    public AppUser getCourier() { return courier; }
    public String getMerchant() { return merchant; }
    public String getPickupCode() { return pickupCode; }
    public String getDeliveryCode() { return deliveryCode; }
    public String getPickupLocation() { return pickupLocation; }
    public String getDestination() { return destination; }
    public PickupStation getPickupStation() { return pickupStation; }
    public DeliveryZone getDeliveryZone() { return deliveryZone; }
    public ServiceType getServiceType() { return serviceType; }
    public boolean isPackageSizeConfirmed() { return packageSizeConfirmed; }
    public ServiceType getProposedPackageServiceType() { return proposedPackageServiceType; }
    public OrderStatus getStatus() { return status; }
    public Instant getArchivedAt() { return archivedAt; }
    public AppUser getArchivedBy() { return archivedBy; }
    public int getFeeCents() { return feeCents; }
    public OrderPaymentStatus getPaymentStatus() { return paymentStatus; }
    public OrderPaymentMethod getPaymentMethod() { return paymentMethod; }
    public int getPaidCents() { return paidCents; }
    public ManualPaymentStatus getManualPaymentStatus() { return manualPaymentStatus; }
    public String getPayerReference() { return payerReference; }
    public Instant getPaymentRequestedAt() { return paymentRequestedAt; }
    public Instant getPaymentSubmittedAt() { return paymentSubmittedAt; }
    public Instant getPaymentReviewedAt() { return paymentReviewedAt; }
    public String getPaymentReviewNote() { return paymentReviewNote; }
    public AppUser getPaymentReviewer() { return paymentReviewer; }
    public CourierSettlementStatus getCourierSettlementStatus() { return courierSettlementStatus; }
    public Instant getCourierSettledAt() { return courierSettledAt; }
    public AppUser getCourierSettlementReviewer() { return courierSettlementReviewer; }
    public int getOriginalFeeCents() { return originalFeeCents == null ? feeCents : originalFeeCents; }
    public int getDiscountCents() { return discountCents == null ? 0 : discountCents; }
    public String getCouponTitle() { return couponTitle; }
    public Long getCouponId() { return couponId; }
    public boolean isMembershipUsed() { return Boolean.TRUE.equals(membershipUsed); }
    public String getMembershipPlanTitle() { return membershipPlanTitle; }
    public Long getMembershipId() { return membershipId; }
    public Integer getRatingScore() { return ratingScore; }
    public String getRatingComment() { return ratingComment; }
    public Instant getRatedAt() { return ratedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Double getCourierLatitude() { return courierLatitude; }
    public Double getCourierLongitude() { return courierLongitude; }
    public Instant getCourierLocationUpdatedAt() { return courierLocationUpdatedAt; }
}
