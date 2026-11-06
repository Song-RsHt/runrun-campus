package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.CourierCertificationStatus;

import java.time.Instant;
import java.time.LocalDate;

/** 管理员骑手查询条目响应；保持现有 JSON 字段及序列化契约。 */
public record AdminCourierItem(
        Long courierId,
        String displayName,
        String phone,
        boolean approved,
        Instant registeredAt,
        long completedOrderCount,
        long completedPackageOrderCount,
        Long certificationId,
        String realName,
        String studentId,
        String campus,
        CourierCertificationStatus status,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt,
        String reviewerName,
        LocalDate validUntil,
        LocalDate approvedValidUntil,
        boolean photoUploaded) {}
