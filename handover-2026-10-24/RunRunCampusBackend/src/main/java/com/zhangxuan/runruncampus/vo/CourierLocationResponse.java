package com.zhangxuan.runruncampus.vo;

import java.time.Instant;




public record CourierLocationResponse(boolean available, Double latitude, Double longitude, Instant updatedAt) {
}
