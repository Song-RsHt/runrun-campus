package com.zhangxuan.runruncampus.dto.response.commandcenter;

import java.util.List;

public record Couriers(long online, long idle, List<CourierItem> items, int page, int size) {}
