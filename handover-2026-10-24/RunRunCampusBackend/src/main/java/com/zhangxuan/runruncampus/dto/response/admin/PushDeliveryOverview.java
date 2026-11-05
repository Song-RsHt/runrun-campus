package com.zhangxuan.runruncampus.dto.response.admin;

import com.zhangxuan.runruncampus.repository.message.PushDeliveryRepository;

public record PushDeliveryOverview(
    long dead, long pending, long processing, PushDeliveryRepository.DeliveryPage deliveries) {}
