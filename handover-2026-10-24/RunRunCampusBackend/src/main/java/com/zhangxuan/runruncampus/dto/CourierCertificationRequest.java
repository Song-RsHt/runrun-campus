package com.zhangxuan.runruncampus.dto;

import java.time.LocalDate;

public record CourierCertificationRequest(String realName, String studentId, String campus, LocalDate validUntil) {
}
