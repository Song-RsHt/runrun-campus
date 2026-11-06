package com.zhangxuan.runrunv2.dto.request.courier;



public record CertificationDecisionRequest(
    String decision, long expectedVersion, String photoResourceId, String reason) {}
