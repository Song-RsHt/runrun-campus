package com.zhangxuan.runruncampus.address;

import com.zhangxuan.runruncampus.user.AppUser;
import com.zhangxuan.runruncampus.order.DeliveryZone;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "user_addresses")
public class UserAddress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Column(nullable = false, length = 32)
    private String label;

    @Column(nullable = false, length = 120)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryZone deliveryZone;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserAddress() {
    }

    public UserAddress(AppUser user, String label, String address, boolean defaultAddress) {
        this(user, label, address, DeliveryZone.fromLegacy(address), defaultAddress);
    }

    public UserAddress(AppUser user, String label, String address, DeliveryZone deliveryZone, boolean defaultAddress) {
        this.user = user;
        this.label = label;
        this.address = address;
        this.deliveryZone = deliveryZone;
        this.defaultAddress = defaultAddress;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void update(String label, String address) {
        update(label, address, deliveryZone);
    }

    public void update(String label, String address, DeliveryZone deliveryZone) {
        this.label = label;
        this.address = address;
        this.deliveryZone = deliveryZone;
        this.updatedAt = Instant.now();
    }

    public void makeDefault() {
        this.defaultAddress = true;
        this.updatedAt = Instant.now();
    }

    public void clearDefault() {
        this.defaultAddress = false;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public AppUser getUser() { return user; }
    public String getLabel() { return label; }
    public String getAddress() { return address; }
    public DeliveryZone getDeliveryZone() { return deliveryZone; }
    public boolean isDefaultAddress() { return defaultAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
