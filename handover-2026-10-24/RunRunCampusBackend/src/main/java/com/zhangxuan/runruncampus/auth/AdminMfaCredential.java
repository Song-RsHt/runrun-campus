package com.zhangxuan.runruncampus.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "admin_mfa_credentials")
class AdminMfaCredential {
    @Id Long userId;
    @Column(columnDefinition = "text") String secretCiphertext;
    @Column(nullable = false) long lastUsedStep = -1;
    @Column(nullable = false, columnDefinition = "text") String recoveryHashes = "";
    @Column(length = 64) String resetGrantHash;
    Instant resetGrantExpiresAt;
    @Column(length = 64, unique = true) String challengeHash;
    Instant challengeExpiresAt;
    @Column(nullable = false) long challengeVersion;
    @Column(nullable = false) int challengeAttempts;
    @Column(length = 16) String challengeKind;
    @Column(columnDefinition = "text") String pendingSecretCiphertext;

    protected AdminMfaCredential() { }
    AdminMfaCredential(Long userId) { this.userId = userId; }

    void clearChallenge() {
        challengeHash = null;
        challengeExpiresAt = null;
        challengeKind = null;
        pendingSecretCiphertext = null;
        challengeAttempts = 0;
    }
}
