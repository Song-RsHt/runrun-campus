package com.zhangxuan.runruncampus.order;

import com.zhangxuan.runruncampus.user.AppUser;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "order_chat_messages")
public class OrderChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CampusOrder order;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser sender;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser recipient;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant readAt;

    protected OrderChatMessage() { }

    public OrderChatMessage(CampusOrder order, AppUser sender, AppUser recipient, String content) {
        this.order = order;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getSender() { return sender; }
    public AppUser getRecipient() { return recipient; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
}
