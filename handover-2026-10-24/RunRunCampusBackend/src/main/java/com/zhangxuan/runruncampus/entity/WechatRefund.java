package com.zhangxuan.runruncampus.entity;

import com.zhangxuan.runruncampus.enums.RefundStatus;
import com.zhangxuan.runruncampus.model.RefundResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wechat_refunds")
public class WechatRefund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, unique = true, length = 32)
    private String outRefundNo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private WechatPayment payment;

    @Column(nullable = false)
    private int amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RefundStatus status;

    @Column(nullable = false, length = 80)
    private String reason;

    @Column(length = 32)
    private String wechatRefundId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected WechatRefund() {
    }

    public WechatRefund(WechatPayment payment, int amountCents, String reason) {
        this.outRefundNo = "RF" + UUID.randomUUID().toString().replace("-", "").substring(0, 30);
        this.payment = payment;
        this.amountCents = amountCents;
        this.reason = reason;
        this.status = RefundStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void record(RefundResult result) {
        this.wechatRefundId = result.refundId();
        this.status = result.status();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOutRefundNo() {
        return outRefundNo;
    }

    public WechatPayment getPayment() {
        return payment;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
