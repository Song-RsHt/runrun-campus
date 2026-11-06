package com.zhangxuan.runruncampus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** 用户优惠券持久化实体；映射既有表和关联，不定义 HTTP 请求字段。 */
@Entity
@Table(name = "user_coupons")
public class UserCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version private Long version;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Column(nullable = false, length = 48)
    private String title;

    @Column(nullable = false)
    private int discountCents;

    @Column(nullable = false)
    private int minimumOrderCents;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant usedAt;

    protected UserCoupon() {}

    public UserCoupon(
            AppUser user,
            String title,
            int discountCents,
            int minimumOrderCents,
            Instant expiresAt) {
        this.user = user;
        this.title = title;
        this.discountCents = discountCents;
        this.minimumOrderCents = minimumOrderCents;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public void markUsed() {
        if (usedAt == null) {
            usedAt = Instant.now();
        }
    }

    public void restore() {
        if (usedAt == null) {
            throw new IllegalStateException("优惠券无需返还");
        }
        usedAt = null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTitle() {
        return title;
    }

    public int getDiscountCents() {
        return discountCents;
    }

    public int getMinimumOrderCents() {
        return minimumOrderCents;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
