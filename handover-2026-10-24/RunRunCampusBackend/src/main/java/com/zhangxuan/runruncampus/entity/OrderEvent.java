package com.zhangxuan.runruncampus.entity;

import com.zhangxuan.runruncampus.enums.OrderStatus;
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
@Table(name = "order_events")
public class OrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CampusOrder order;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderEvent() {
    }

    public OrderEvent(CampusOrder order, AppUser actor, OrderStatus status) {
        this.order = order;
        this.actor = actor;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getActor() {
        return actor;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
