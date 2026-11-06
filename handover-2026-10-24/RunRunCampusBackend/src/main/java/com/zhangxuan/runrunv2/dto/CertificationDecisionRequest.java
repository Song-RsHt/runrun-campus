package com.zhangxuan.runrunv2.dto;

/** 资质决定请求参数；保留入口的字段、构造器及校验契约。 */
public record CertificationDecisionRequest(
        String decision, long expectedVersion, String photoResourceId, String reason) {}
