package com.zhangxuan.runruncampus.user;

import com.zhangxuan.runruncampus.admin.AdminRole;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String phone;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 48)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private AdminRole adminRole;

    private Boolean courierApproved;

    private Boolean courierAcceptingOrders;

    private Instant courierAvailabilityUpdatedAt;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int failedLoginAttempts;

    private Instant loginLockedUntil;

    @Column(nullable = false)
    private long securityVersion;

    @Column(length = 32)
    private String privacyPolicyVersion;

    private Instant privacyConsentAt;

    private Instant deletedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
    }

    public AppUser(String phone, String passwordHash, String displayName, UserRole role) {
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.adminRole = role == UserRole.ADMIN ? AdminRole.SUPER_ADMIN : null;
        this.enabled = true;
        this.failedLoginAttempts = 0;
        this.createdAt = Instant.now();
    }

    public void markNewCourierUnapproved() {
        if (role == UserRole.COURIER) {
            courierApproved = false;
            courierAcceptingOrders = false;
        }
    }

    public void approveCourier() {
        if (role == UserRole.COURIER) courierApproved = true;
    }

    public void revokeCourierApproval() {
        if (role == UserRole.COURIER) {
            courierApproved = false;
            setCourierAcceptingOrders(false);
        }
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public void changePasswordHash(String passwordHash) {
        this.passwordHash = java.util.Objects.requireNonNull(passwordHash);
        invalidateAdminAuthentication();
    }
    public String getDisplayName() { return displayName; }
    public String getPrivacyPolicyVersion() { return privacyPolicyVersion; }
    public Instant getPrivacyConsentAt() { return privacyConsentAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void recordPrivacyConsent(String version) {
        privacyPolicyVersion = java.util.Objects.requireNonNull(version);
        privacyConsentAt = Instant.now();
    }
    public void withdrawPrivacyConsent() {
        privacyPolicyVersion = null;
        privacyConsentAt = null;
    }
    public void anonymize(String deletedPasswordHash) {
        if (id == null) throw new IllegalStateException("未保存的账号不能注销");
        phone = "deleted-" + id;
        displayName = "已注销用户";
        passwordHash = java.util.Objects.requireNonNull(deletedPasswordHash);
        enabled = false;
        failedLoginAttempts = 0;
        loginLockedUntil = null;
        if (role == UserRole.COURIER) {
            courierApproved = false;
            courierAcceptingOrders = false;
            courierAvailabilityUpdatedAt = Instant.now();
        }
        deletedAt = Instant.now();
    }
    public UserRole getRole() { return role; }
    public AdminRole getAdminRole() { return adminRole; }
    public void changeAdminRole(AdminRole adminRole) {
        if (role != UserRole.ADMIN) throw new IllegalStateException("非管理员不能分配管理员岗位");
        this.adminRole = java.util.Objects.requireNonNull(adminRole);
        invalidateAdminAuthentication();
    }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isEnabled() { return enabled; }
    public void suspend() { enabled = false; invalidateAdminAuthentication(); }
    public void restore() { enabled = true; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public long getSecurityVersion() { return securityVersion; }
    public void invalidateAdminAuthentication() { if (role == UserRole.ADMIN) securityVersion++; }
    public Instant getLoginLockedUntil() { return loginLockedUntil; }
    public boolean isLoginLocked(Instant now) { return loginLockedUntil != null && loginLockedUntil.isAfter(now); }
    public void recordLoginFailure(int maximumAttempts, Instant lockUntil) {
        if (loginLockedUntil != null && !loginLockedUntil.isAfter(Instant.now())) clearLoginFailures();
        failedLoginAttempts++;
        if (failedLoginAttempts >= maximumAttempts) loginLockedUntil = lockUntil;
    }
    public void clearLoginFailures() {
        failedLoginAttempts = 0;
        loginLockedUntil = null;
    }
    public boolean isCourierApproved() {
        return role == UserRole.COURIER && !Boolean.FALSE.equals(courierApproved);
    }
    public boolean isCourierAcceptingOrders() {
        return role == UserRole.COURIER && !Boolean.FALSE.equals(courierAcceptingOrders);
    }
    public void setCourierAcceptingOrders(boolean acceptingOrders) {
        if (role != UserRole.COURIER) throw new IllegalStateException("只有骑手可以设置接单状态");
        this.courierAcceptingOrders = acceptingOrders;
        this.courierAvailabilityUpdatedAt = Instant.now();
    }
    public Instant getCourierAvailabilityUpdatedAt() { return courierAvailabilityUpdatedAt; }
}
