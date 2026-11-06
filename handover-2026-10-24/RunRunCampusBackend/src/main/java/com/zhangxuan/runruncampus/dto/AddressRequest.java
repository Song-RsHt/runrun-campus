package com.zhangxuan.runruncampus.dto;

import com.zhangxuan.runruncampus.enums.DeliveryZone;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;






public record AddressRequest(@NotBlank(message = "请输入地址标签") @Size(max = 32, message = "地址标签过长") String label, @NotBlank(message = "请输入详细地址") @Size(max = 120, message = "详细地址过长") String address, DeliveryZone deliveryZone, boolean defaultAddress) {

    public AddressRequest(String label, String address, boolean defaultAddress) {
        this(label, address, DeliveryZone.fromLegacy(address), defaultAddress);
    }
}
