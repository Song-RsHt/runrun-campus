package com.zhangxuan.runruncampus.message;

import com.zhangxuan.runruncampus.order.CampusOrder;
import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "order_messages")
public class OrderMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser recipient;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CampusOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderMessageType type;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 600)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant readAt;

    protected OrderMessage() {
    }

    public OrderMessage(AppUser recipient, CampusOrder order, OrderMessageType type, String title, String content) {
        this.recipient = recipient;
        this.order = order;
        this.type = type;
        this.title = title;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public void markRead() {
        if (readAt == null) readAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getRecipient() { return recipient; }
    public CampusOrder getOrder() { return order; }
    public OrderMessageType getType() { return type; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public boolean isRead() { return readAt != null; }
}
