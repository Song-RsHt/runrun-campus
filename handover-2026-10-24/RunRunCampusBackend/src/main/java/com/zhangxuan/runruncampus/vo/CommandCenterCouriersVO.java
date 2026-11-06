package com.zhangxuan.runruncampus.vo;

import java.util.List;

public record CommandCenterCouriersVO(long online, long idle, List<CourierItem> items, int page, int size) {
}
