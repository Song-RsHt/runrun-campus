package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;
import java.time.Instant;
import java.time.LocalDate;






























public record CertificationResponse(Long id, Long courierId, String courierName, String phone, String realName, String studentId, String campus, CourierCertificationStatus status, String rejectionReason, Instant submittedAt, Instant reviewedAt, String reviewerName, boolean approved, LocalDate validUntil, LocalDate approvedValidUntil, boolean photoUploaded) {
}
