package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.enums.Kind;
import java.time.OffsetDateTime;
import java.util.List;

public record LocationEventVO(String eventId, OffsetDateTime receivedAt, boolean accepted, long orderVersion) {
}
