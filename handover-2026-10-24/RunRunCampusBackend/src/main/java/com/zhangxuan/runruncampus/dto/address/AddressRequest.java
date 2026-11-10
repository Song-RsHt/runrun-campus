package com.zhangxuan.runruncampus.dto.address;

import com.zhangxuan.runruncampus.enums.order.DeliveryZone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 收件地址请求参数；保留入口的字段、构造器及校验契约。 */
public record AddressRequest(
        @NotBlank(message = "请输入地址标签") @Size(max = 32, message = "地址标签过长") String label,
        @NotBlank(message = "请输入详细地址") @Size(max = 120, message = "详细地址过长") String address,
        DeliveryZone deliveryZone,
        boolean defaultAddress) {

    public AddressRequest(String label, String address, boolean defaultAddress) {
        this(label, address, DeliveryZone.fromLegacy(address), defaultAddress);
    }
}
