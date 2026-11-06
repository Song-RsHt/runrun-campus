package com.zhangxuan.runrunv2.dto;



public record CertificationDecisionRequest(String decision, long expectedVersion, String photoResourceId, String reason) {
}
