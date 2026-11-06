package com.zhangxuan.runrunv2.vo;

/** 投递区域响应；保持现有 JSON 字段及序列化契约。 */
public record DeliveryZoneVO(
        String id,
        String code,
        String name,
        long packageSurchargeCents,
        boolean enabled,
        long configVersion,
        long version) {}
