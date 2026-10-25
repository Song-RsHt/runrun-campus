package com.zhangxuan.runruncampus.order;

public enum PickupStation {
    EAST_GATE_TAKEOUT("东门外卖集中取餐点", false),
    CAMPUS_PACKAGE_STATION("校内快递驿站", true);

    private final String displayName;
    private final boolean packageStation;

    PickupStation(String displayName, boolean packageStation) {
        this.displayName = displayName;
        this.packageStation = packageStation;
    }

    public String getDisplayName() { return displayName; }

    public boolean supports(ServiceType serviceType) {
        return packageStation == serviceType.isPackageDelivery();
    }

    public static PickupStation fromLegacy(ServiceType serviceType) {
        return serviceType.isPackageDelivery() ? CAMPUS_PACKAGE_STATION : EAST_GATE_TAKEOUT;
    }
}
