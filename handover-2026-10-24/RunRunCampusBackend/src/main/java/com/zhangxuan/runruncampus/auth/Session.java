package com.zhangxuan.runruncampus.auth;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "user_sessions")
public class Session {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String token;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean adminMfaVerified;

    @Column(nullable = false)
    private long securityVersion;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 80)
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionClientType clientType;

    protected Session() {
    }

    public Session(String token, AppUser user, Instant expiresAt) {
        this(token, user, expiresAt, SessionClientType.UNKNOWN, "未知设备");
    }

    public Session(String token, AppUser user, Instant expiresAt, SessionClientType clientType, String deviceName) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
        this.securityVersion = user.getSecurityVersion();
        this.createdAt = Instant.now();
        this.clientType = clientType == null ? SessionClientType.UNKNOWN : clientType;
        String normalizedName = deviceName == null ? "" : deviceName.trim();
        this.deviceName = normalizedName.isEmpty() ? defaultDeviceName(this.clientType)
                : normalizedName.substring(0, Math.min(normalizedName.length(), 80));
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public AppUser getUser() { return user; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isAdminMfaVerified() { return adminMfaVerified; }
    public long getSecurityVersion() { return securityVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDeviceName() { return deviceName; }
    public SessionClientType getClientType() { return clientType; }
    void markAdminMfaVerified() { adminMfaVerified = true; }

    private static String defaultDeviceName(SessionClientType type) {
        return type == SessionClientType.ANDROID ? "Android 设备"
                : type == SessionClientType.ADMIN_WEB ? "运营后台浏览器" : "未知设备";
    }
}
