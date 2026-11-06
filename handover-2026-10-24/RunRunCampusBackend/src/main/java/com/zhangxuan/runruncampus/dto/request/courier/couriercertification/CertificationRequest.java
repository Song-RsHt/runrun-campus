package com.zhangxuan.runruncampus.dto.request.courier.couriercertification;

import java.time.LocalDate;

public record CertificationRequest(
    String realName, String studentId, String campus, LocalDate validUntil) {}
