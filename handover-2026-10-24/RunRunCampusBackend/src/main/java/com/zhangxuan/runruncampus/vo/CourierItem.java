package com.zhangxuan.runruncampus.vo;

import java.time.Instant;




public record CourierItem(long id, String displayName, long activeOrders, Instant onlineSince) {
}
