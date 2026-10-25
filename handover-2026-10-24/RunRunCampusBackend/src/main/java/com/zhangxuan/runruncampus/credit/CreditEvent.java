package com.zhangxuan.runruncampus.credit;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "credit_events")
public class CreditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private AppUser operator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CreditEventType eventType;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false)
    private int balanceAfter;

    @Column(nullable = false, length = 300)
    private String reason;

    private Long riskSignalId;
    private Long originalEventId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CreditEvent() { }

    public CreditEvent(AppUser user, AppUser operator, CreditEventType eventType, int delta,
                       int balanceAfter, String reason, Long riskSignalId, Long originalEventId) {
        this.user = user;
        this.operator = operator;
        this.eventType = eventType;
        this.delta = delta;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.riskSignalId = riskSignalId;
        this.originalEventId = originalEventId;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public AppUser getOperator() { return operator; }
    public CreditEventType getType() { return eventType; }
    public int getDelta() { return delta; }
    public int getBalanceAfter() { return balanceAfter; }
    public String getReason() { return reason; }
    public Long getRiskSignalId() { return riskSignalId; }
    public Long getOriginalEventId() { return originalEventId; }
    public Instant getCreatedAt() { return createdAt; }
}
