package com.zhangxuan.runrunv2.dto.order;

import com.zhangxuan.runrunv2.model.order.OrderSchedule;

import java.util.List;

/** 服务配置变更内容及预期版本请求参数；保留入口的字段、构造器及校验契约。 */
public record ServiceConfigChangeRequest(
        Long configVersion,
        Long expectedVersion,
        Boolean newOrderEnabled,
        String reviewOwnerId,
        OrderSchedule businessSchedule,
        List<DeliveryZoneChangeRequest> zones,
        List<ServicePointChangeRequest> servicePoints,
        String reason) {}
