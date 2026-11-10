package com.zhangxuan.runruncampus.vo.commandcenter;

import com.zhangxuan.runruncampus.enums.commandcenter.Queue;

import java.util.List;

/** 指挥台订单队列响应；保持现有 JSON 字段及序列化契约。 */
public record CommandCenterOrdersVO(
        long waiting,
        long lateWaiting,
        long active,
        long lateDelivery,
        Queue queue,
        List<OrderItem> items,
        int page,
        int size,
        long totalElements) {}
