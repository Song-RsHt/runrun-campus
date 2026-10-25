package com.zhangxuan.runruncampus.admin;

import com.zhangxuan.runruncampus.user.AppUser;
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

import java.time.Instant;

@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser administrator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdminAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AdminAuditTargetType targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 300)
    private String summary;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditLog() {
    }

    public AdminAuditLog(AppUser administrator, AdminAuditAction action,
                         AdminAuditTargetType targetType, Long targetId, String summary) {
        this.administrator = administrator;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.summary = summary;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getAdministrator() { return administrator; }
    public AdminAuditAction getAction() { return action; }
    public AdminAuditTargetType getTargetType() { return targetType; }
    public Long getTargetId() { return targetId; }
    public String getSummary() { return summary; }
    public Instant getCreatedAt() { return createdAt; }
}
