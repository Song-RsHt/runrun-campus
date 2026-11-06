package com.zhangxuan.runrunv2.dto;



public record CancelRequest(Long expectedVersion, String reason) {
}
