package com.zhangxuan.runruncampus.vo.address;

import com.zhangxuan.runruncampus.enums.order.DeliveryZone;

import java.time.Instant;

/** 收件地址响应；保持现有 JSON 字段及序列化契约。 */
public record AddressResponse(
        Long id,
        String label,
        String address,
        DeliveryZone deliveryZone,
        boolean defaultAddress,
        Instant createdAt,
        Instant updatedAt) {}
