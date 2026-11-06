package com.zhangxuan.runrunv2.vo.order;

import com.zhangxuan.runrunv2.model.order.OrderSchedule;

import java.util.List;

/** 新系统服务配置及计价响应；保持现有 JSON 字段及序列化契约。 */
public record ServiceConfigVO(
        List<ServiceOptionVO> services,
        List<DeliveryZoneVO> zones,
        List<ServicePointVO> servicePoints,
        OrderSchedule businessSchedule,
        boolean newOrderEnabled,
        String reviewOwnerId,
        long configVersion,
        String id,
        long version) {}
