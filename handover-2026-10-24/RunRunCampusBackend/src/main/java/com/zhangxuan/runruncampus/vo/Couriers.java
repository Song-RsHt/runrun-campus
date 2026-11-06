package com.zhangxuan.runruncampus.vo;

import java.util.List;

public record Couriers(long online, long idle, List<CourierItem> items, int page, int size) {
}
