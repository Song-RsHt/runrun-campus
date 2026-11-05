package com.zhangxuan.runruncampus.model.settings;


public record Settings(
    String businessHours,
    int waitMinutes,
    int deliveryMinutes,
    int maxActiveOrders,
    int issuePendingMinutes,
    int issueProcessingMinutes) {}
