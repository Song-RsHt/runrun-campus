package com.zhangxuan.runruncampus.feedback;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "user_feedback")
public class UserFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private FeedbackType type;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackHandlingStatus handlingStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser assignee;

    @Column(length = 500)
    private String resolution;

    private Instant handledAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserFeedback() {
    }

    public UserFeedback(AppUser user, FeedbackType type, String content) {
        this.user = user;
        this.type = type;
        this.content = content;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.handlingStatus = FeedbackHandlingStatus.PENDING;
    }

    public void claim(AppUser administrator) {
        this.assignee = administrator;
        this.handlingStatus = FeedbackHandlingStatus.PROCESSING;
        this.updatedAt = Instant.now();
    }

    public void resolve(String resolution) {
        this.resolution = resolution;
        this.handlingStatus = FeedbackHandlingStatus.RESOLVED;
        this.handledAt = Instant.now();
        this.updatedAt = handledAt;
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public FeedbackType getType() { return type; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public FeedbackHandlingStatus getHandlingStatus() { return handlingStatus; }
    public AppUser getAssignee() { return assignee; }
    public String getResolution() { return resolution; }
    public Instant getHandledAt() { return handledAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
