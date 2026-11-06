package com.zhangxuan.runruncampus.dto;

import java.time.LocalDate;

/** 旧系统骑手资质请求参数；保留入口的字段、构造器及校验契约。 */
public record CourierCertificationRequest(
        String realName, String studentId, String campus, LocalDate validUntil) {}
