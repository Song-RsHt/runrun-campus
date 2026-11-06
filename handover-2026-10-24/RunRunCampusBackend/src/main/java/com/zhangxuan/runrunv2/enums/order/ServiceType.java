package com.zhangxuan.runrunv2.enums.order;

public enum ServiceType {
  LOBBY("楼下取餐", 200, false),
  DOOR("送到门口", 300, false),
  EXPRESS("加急配送", 500, false),
  PACKAGE_SMALL("小件", 100, true),
  PACKAGE_MEDIUM("中件", 200, true),
  PACKAGE_MEDIUM_LARGE("中大件", 300, true),
  PACKAGE_LARGE("大件", 400, true),
  PACKAGE_EXTRA_LARGE("特大件", 500, true),
  PACKAGE_XL("超大件", 800, true);

  public final String name;

  public final long baseCents;

  public final boolean zoneSurcharge;

  ServiceType(String name, long cents, boolean surcharge) {
    this.name = name;
    this.baseCents = cents;
    this.zoneSurcharge = surcharge;
  }
}
