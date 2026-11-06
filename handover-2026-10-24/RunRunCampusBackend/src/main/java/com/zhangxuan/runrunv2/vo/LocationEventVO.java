package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;






public record LocationEventVO(String eventId, OffsetDateTime receivedAt, boolean accepted, long orderVersion) {
}
