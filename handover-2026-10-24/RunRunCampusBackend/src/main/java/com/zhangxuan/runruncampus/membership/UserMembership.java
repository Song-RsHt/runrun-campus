package com.zhangxuan.runruncampus.membership;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "user_memberships")
public class UserMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private MembershipPlanType plan;

    @Column(nullable = false)
    private int totalTrips;

    @Column(nullable = false)
    private int remainingTrips;

    @Column(nullable = false)
    private int priceCents;

    @Column(nullable = false, updatable = false)
    private Instant purchasedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    protected UserMembership() {
    }

    public UserMembership(AppUser user, MembershipPlanType plan, Instant expiresAt) {
        this.user = user;
        this.plan = plan;
        this.totalTrips = plan.getTrips();
        this.remainingTrips = plan.getTrips();
        this.priceCents = plan.getPriceCents();
        this.purchasedAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public void consumeTrip() {
        if (remainingTrips <= 0) throw new IllegalStateException("月卡次数已用完");
        remainingTrips--;
    }

    public void refundTrip() {
        if (remainingTrips >= totalTrips) throw new IllegalStateException("月卡次数无需返还");
        remainingTrips++;
    }

    public boolean isActive(Instant now) { return remainingTrips > 0 && expiresAt.isAfter(now); }
    public boolean belongsTo(AppUser candidate) { return user.getId().equals(candidate.getId()); }
    public Long getId() { return id; }
    public MembershipPlanType getPlan() { return plan; }
    public int getTotalTrips() { return totalTrips; }
    public int getRemainingTrips() { return remainingTrips; }
    public int getPriceCents() { return priceCents; }
    public Instant getPurchasedAt() { return purchasedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
