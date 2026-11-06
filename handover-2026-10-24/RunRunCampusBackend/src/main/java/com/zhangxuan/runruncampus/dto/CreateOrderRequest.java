package com.zhangxuan.runruncampus.dto;

import com.zhangxuan.runruncampus.enums.DeliveryZone;
import com.zhangxuan.runruncampus.enums.PickupStation;
import com.zhangxuan.runruncampus.enums.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(@NotBlank(message = "请输入商家名称") @Size(max = 80) String merchant, @NotBlank(message = "请输入取餐码") @Size(max = 32) String pickupCode, @NotBlank(message = "请输入取餐点") @Size(max = 120) String pickupLocation, @NotBlank(message = "请输入送达位置") @Size(max = 120) String destination, @NotNull(message = "请选择服务方式") ServiceType serviceType, Long couponId, boolean useMembership, PickupStation pickupStation, DeliveryZone deliveryZone) {

    public CreateOrderRequest(String merchant, String pickupCode, String pickupLocation, String destination, ServiceType serviceType, Long couponId, boolean useMembership) {
        this(merchant, pickupCode, pickupLocation, destination, serviceType, couponId, useMembership, PickupStation.fromLegacy(serviceType), DeliveryZone.fromLegacy(destination));
    }
}
