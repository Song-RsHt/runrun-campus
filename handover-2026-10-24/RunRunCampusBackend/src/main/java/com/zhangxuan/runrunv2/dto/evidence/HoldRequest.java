package com.zhangxuan.runrunv2.dto.evidence;

import java.time.Instant;

/** 保全请求参数；保留入口的字段、构造器及校验契约。 */
public record HoldRequest(Long orderId, Long certificationId, String reason, Instant reviewDueAt) {}
