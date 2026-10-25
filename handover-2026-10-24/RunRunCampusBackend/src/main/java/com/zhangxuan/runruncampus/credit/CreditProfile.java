package com.zhangxuan.runruncampus.credit;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "credit_profiles")
public class CreditProfile {
    @Id
    private Long userId;

    @Column(nullable = false)
    private int score;

    @Version
    private Long version;

    @Column(nullable = false)
    private Instant updatedAt;

    protected CreditProfile() { }

    public CreditProfile(AppUser user) {
        if (user.getId() == null) throw new IllegalArgumentException("用户必须先保存");
        this.userId = user.getId();
        this.score = 100;
        this.updatedAt = Instant.now();
    }

    public int apply(int requestedDelta) {
        int before = score;
        score = Math.max(0, Math.min(100, score + requestedDelta));
        updatedAt = Instant.now();
        return score - before;
    }

    public Long getUserId() { return userId; }
    public int getScore() { return score; }
    public Instant getUpdatedAt() { return updatedAt; }
    public CreditRiskLevel getRiskLevel() { return CreditRiskLevel.fromScore(score); }
}
