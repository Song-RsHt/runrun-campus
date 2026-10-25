package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "order_issues")
public class OrderIssue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CampusOrder order;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 32)
    private OrderIssueType type;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderIssueHandlingStatus handlingStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser assignee;

    @Column(length = 500)
    private String resolution;

    private Instant handledAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected OrderIssue() {
    }

    public OrderIssue(CampusOrder order, AppUser reporter, OrderIssueType type, String description) {
        this.order = order;
        this.reporter = reporter;
        this.type = type;
        this.description = description;
        this.createdAt = Instant.now();
        this.handlingStatus = OrderIssueHandlingStatus.PENDING;
        this.updatedAt = this.createdAt;
    }

    public void claim(AppUser administrator) {
        handlingStatus = OrderIssueHandlingStatus.PROCESSING;
        assignee = administrator;
        updatedAt = Instant.now();
    }

    public void resolve(String resolution) {
        handlingStatus = OrderIssueHandlingStatus.RESOLVED;
        this.resolution = resolution;
        handledAt = Instant.now();
        updatedAt = handledAt;
    }

    public Long getId() { return id; }
    public CampusOrder getOrder() { return order; }
    public AppUser getReporter() { return reporter; }
    public OrderIssueType getType() { return type; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public OrderIssueHandlingStatus getHandlingStatus() { return handlingStatus; }
    public AppUser getAssignee() { return assignee; }
    public String getResolution() { return resolution; }
    public Instant getHandledAt() { return handledAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
