package com.zhangxuan.runrunv2.vo;

import java.time.Instant;

/** 旧系统骑手资质响应；保持现有 JSON 字段及序列化契约。 */
public record CourierCertificationVO(
        long id,
        long courierId,
        String status,
        boolean eligible,
        long version,
        Instant submittedAt,
        Instant reviewedAt,
        boolean photoUploaded,
        String photoResourceId,
        String rejectionReason) {}
