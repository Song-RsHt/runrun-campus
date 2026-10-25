package com.zhangxuan.runruncampus.payment;

import com.zhangxuan.runruncampus.membership.MembershipPlanType;
import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wechat_payments")
public class WechatPayment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @Column(nullable = false, unique = true, length = 32) private String outTradeNo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PaymentBusinessType businessType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PaymentStatus status;
    @Column(nullable = false) private int amountCents;
    @Column(unique = true, length = 32) private String transactionId;
    @Enumerated(EnumType.STRING) @Column(length = 24) private MembershipPlanType membershipPlan;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY) private CampusOrder order;
    @Column(length = 32) private String appId;
    @Column(length = 32) private String partnerId;
    @Column(length = 64) private String prepayId;
    @Column(length = 32) private String packageValue;
    @Column(length = 64) private String nonceStr;
    @Column(length = 32) private String payTimestamp;
    @Column(length = 512) private String paySignature;
    @Column(nullable = false) private Instant expiresAt;
    private Instant paidAt;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected WechatPayment() { }

    private WechatPayment(AppUser user, CampusOrder order, MembershipPlanType plan, int amountCents, Instant expiresAt) {
        this.outTradeNo = "RR" + UUID.randomUUID().toString().replace("-", "").substring(0, 30);
        this.businessType = order == null ? PaymentBusinessType.MEMBERSHIP : PaymentBusinessType.ORDER;
        this.status = PaymentStatus.PENDING;
        this.amountCents = amountCents;
        this.user = user;
        this.order = order;
        this.membershipPlan = plan;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static WechatPayment forOrder(AppUser user, CampusOrder order, int amountCents, Instant expiresAt) {
        return new WechatPayment(user, order, null, amountCents, expiresAt);
    }

    public static WechatPayment forMembership(AppUser user, MembershipPlanType plan, Instant expiresAt) {
        return new WechatPayment(user, null, plan, plan.getPriceCents(), expiresAt);
    }

    public void recordPrepay(WechatPayGateway.AppPayParameters parameters) {
        this.appId = parameters.appId();
        this.partnerId = parameters.partnerId();
        this.prepayId = parameters.prepayId();
        this.packageValue = parameters.packageValue();
        this.nonceStr = parameters.nonceStr();
        this.payTimestamp = parameters.timestamp();
        this.paySignature = parameters.sign();
        this.updatedAt = Instant.now();
    }

    public void markPaid(String transactionId) {
        if (status == PaymentStatus.PAID) return;
        if (status == PaymentStatus.CLOSED) throw new IllegalStateException("已关闭的支付单不能入账");
        this.status = PaymentStatus.PAID;
        this.transactionId = transactionId;
        this.paidAt = Instant.now();
        this.updatedAt = paidAt;
    }

    public void close() {
        if (status == PaymentStatus.PAID) throw new IllegalStateException("已支付的支付单不能关闭");
        status = PaymentStatus.CLOSED;
        updatedAt = Instant.now();
    }

    public boolean hasPayParameters() { return prepayId != null; }
    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
    public boolean belongsTo(AppUser candidate) { return user.getId().equals(candidate.getId()); }
    public Long getId() { return id; }
    public String getOutTradeNo() { return outTradeNo; }
    public PaymentBusinessType getBusinessType() { return businessType; }
    public PaymentStatus getStatus() { return status; }
    public int getAmountCents() { return amountCents; }
    public String getTransactionId() { return transactionId; }
    public MembershipPlanType getMembershipPlan() { return membershipPlan; }
    public AppUser getUser() { return user; }
    public CampusOrder getOrder() { return order; }
    public String getAppId() { return appId; }
    public String getPartnerId() { return partnerId; }
    public String getPrepayId() { return prepayId; }
    public String getPackageValue() { return packageValue; }
    public String getNonceStr() { return nonceStr; }
    public String getPayTimestamp() { return payTimestamp; }
    public String getPaySignature() { return paySignature; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getPaidAt() { return paidAt; }
}
