package com.zhangxuan.runruncampus.order;

public enum DeliveryZone {
    STANDARD("其他区域", 0),
    MEIYUAN("梅园", 200);

    private final String displayName;
    private final int packageSurchargeCents;

    DeliveryZone(String displayName, int packageSurchargeCents) {
        this.displayName = displayName;
        this.packageSurchargeCents = packageSurchargeCents;
    }

    public String getDisplayName() { return displayName; }
    public int getPackageSurchargeCents() { return packageSurchargeCents; }

    public static DeliveryZone fromLegacy(String destination) {
        return destination != null && destination.contains("梅园") ? MEIYUAN : STANDARD;
    }
}
