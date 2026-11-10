package com.zhangxuan.runruncampus.vo.courier;

import com.zhangxuan.runruncampus.enums.courier.CourierCertificationStatus;

import java.time.Instant;
import java.time.LocalDate;

/** 资质响应；保持现有 JSON 字段及序列化契约。 */
public record CertificationResponse(
        Long id,
        Long courierId,
        String courierName,
        String phone,
        String realName,
        String studentId,
        String campus,
        CourierCertificationStatus status,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt,
        String reviewerName,
        boolean approved,
        LocalDate validUntil,
        LocalDate approvedValidUntil,
        boolean photoUploaded) {}
