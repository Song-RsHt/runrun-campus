package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.model.OrderSchedule;

import java.util.List;

/** 新系统服务配置及计价变更请求参数；保留入口的字段、构造器及校验契约。 */
public record ServiceConfigChangeRequest(
        Long configVersion,
        Long expectedVersion,
        Boolean newOrderEnabled,
        String reviewOwnerId,
        OrderSchedule businessSchedule,
        List<DeliveryZoneChangeRequest> zones,
        List<ServicePointChangeRequest> servicePoints,
        String reason) {}
