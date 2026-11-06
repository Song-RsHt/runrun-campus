package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.DeliveryZone;
import java.time.Instant;














public record AddressResponse(Long id, String label, String address, DeliveryZone deliveryZone, boolean defaultAddress, Instant createdAt, Instant updatedAt) {
}
