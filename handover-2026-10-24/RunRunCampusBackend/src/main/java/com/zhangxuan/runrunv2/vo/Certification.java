package com.zhangxuan.runrunv2.vo;

import java.time.Instant;

public record Certification(long id, long courierId, String status, boolean eligible, long version, Instant submittedAt, Instant reviewedAt, boolean photoUploaded, String photoResourceId, String rejectionReason) {
}
