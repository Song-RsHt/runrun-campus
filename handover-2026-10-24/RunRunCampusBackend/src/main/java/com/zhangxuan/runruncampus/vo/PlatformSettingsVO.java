package com.zhangxuan.runruncampus.vo;

/** 平台运行设置响应；保持现有 JSON 字段及序列化契约。 */
public record PlatformSettingsVO(
        String businessHours,
        int waitMinutes,
        int deliveryMinutes,
        int maxActiveOrders,
        int issuePendingMinutes,
        int issueProcessingMinutes) {}
