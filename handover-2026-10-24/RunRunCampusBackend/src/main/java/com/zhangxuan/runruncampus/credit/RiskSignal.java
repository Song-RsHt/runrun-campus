package com.zhangxuan.runruncampus.credit;

import com.zhangxuan.runruncampus.order.CampusOrder;
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
@Table(name = "risk_signals")
public class RiskSignal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_user_id")
    private AppUser subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 32)
    private RiskSignalType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RiskSignalStatus status;

    @Column(nullable = false, unique = true, length = 120)
    private String sourceKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CampusOrder order;

    @Column(nullable = false, length = 300)
    private String details;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private AppUser reviewedBy;

    @Column(length = 300)
    private String reviewNote;

    protected RiskSignal() { }

    public RiskSignal(AppUser subject, RiskSignalType type, String sourceKey,
                      CampusOrder order, String details) {
        this.subject = subject;
        this.type = type;
        this.sourceKey = sourceKey;
        this.order = order;
        this.details = details;
        this.status = RiskSignalStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void review(RiskSignalStatus status, AppUser reviewer, String note) {
        if (this.status != RiskSignalStatus.PENDING) throw new IllegalStateException("风险信号已经处理");
        if (status == RiskSignalStatus.PENDING) throw new IllegalArgumentException("处理状态无效");
        this.status = status;
        this.reviewedBy = reviewer;
        this.reviewNote = note;
        this.reviewedAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getSubject() { return subject; }
    public RiskSignalType getType() { return type; }
    public RiskSignalStatus getStatus() { return status; }
    public String getDetails() { return details; }
    public CampusOrder getOrder() { return order; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public AppUser getReviewedBy() { return reviewedBy; }
    public String getReviewNote() { return reviewNote; }
}
