package com.zhangxuan.runrunv2.dto;

public record DeliveryZoneChangeRequest(String code, String name, Long packageSurchargeCents, Boolean enabled) {
}
