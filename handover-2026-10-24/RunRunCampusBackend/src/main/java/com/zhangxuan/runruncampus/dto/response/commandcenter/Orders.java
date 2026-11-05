package com.zhangxuan.runruncampus.dto.response.commandcenter;

import com.zhangxuan.runruncampus.enums.commandcenter.Queue;
import java.util.List;

public record Orders(
    long waiting,
    long lateWaiting,
    long active,
    long lateDelivery,
    Queue queue,
    List<OrderItem> items,
    int page,
    int size,
    long totalElements) {}
