package com.zhangxuan.runruncampus.vo.admin;

import com.zhangxuan.runruncampus.vo.message.PushDeliveryPageVO;

/** 推送投递概览响应；保持现有 JSON 字段及序列化契约。 */
public record PushDeliveryOverview(
        long dead, long pending, long processing, PushDeliveryPageVO deliveries) {}
