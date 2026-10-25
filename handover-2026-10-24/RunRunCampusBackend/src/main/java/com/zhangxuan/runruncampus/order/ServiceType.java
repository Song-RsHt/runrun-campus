package com.zhangxuan.runruncampus.order;

public enum ServiceType {
    LOBBY(200),
    DOOR(300),
    EXPRESS(500),
    PACKAGE_SMALL(100),
    PACKAGE_MEDIUM(200),
    PACKAGE_MEDIUM_LARGE(300),
    PACKAGE_LARGE(400),
    PACKAGE_EXTRA_LARGE(500),
    PACKAGE_XL(800);

    private final int feeCents;

    ServiceType(int feeCents) {
        this.feeCents = feeCents;
    }

    public int getFeeCents() {
        return feeCents;
    }

    public int getFeeCents(DeliveryZone deliveryZone) {
        return feeCents + (isPackageDelivery() ? deliveryZone.getPackageSurchargeCents() : 0);
    }

    public boolean isPackageDelivery() {
        return this == PACKAGE_SMALL || this == PACKAGE_MEDIUM || this == PACKAGE_MEDIUM_LARGE
                || this == PACKAGE_LARGE || this == PACKAGE_EXTRA_LARGE || this == PACKAGE_XL;
    }
}
