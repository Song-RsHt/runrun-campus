package com.zhangxuan.runruncampus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** 管理员双因素认证凭据持久化实体；映射既有表和关联，不定义 HTTP 请求字段。 */
@Entity
@Table(name = "admin_mfa_credentials")
public class AdminMfaCredential {

    @Id public Long userId;

    @Column(columnDefinition = "text")
    public String secretCiphertext;

    @Column(nullable = false)
    public long lastUsedStep = -1;

    @Column(nullable = false, columnDefinition = "text")
    public String recoveryHashes = "";

    @Column(length = 64)
    public String resetGrantHash;

    public Instant resetGrantExpiresAt;

    @Column(length = 64, unique = true)
    public String challengeHash;

    public Instant challengeExpiresAt;

    @Column(nullable = false)
    public long challengeVersion;

    @Column(nullable = false)
    public int challengeAttempts;

    @Column(length = 16)
    public String challengeKind;

    @Column(columnDefinition = "text")
    public String pendingSecretCiphertext;

    protected AdminMfaCredential() {}

    public AdminMfaCredential(Long userId) {
        this.userId = userId;
    }

    public void clearChallenge() {
        challengeHash = null;
        challengeExpiresAt = null;
        challengeKind = null;
        pendingSecretCiphertext = null;
        challengeAttempts = 0;
    }
}
