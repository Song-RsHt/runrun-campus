package com.zhangxuan.runruncampus.vo;

public record PlatformSettingsVO(String businessHours, int waitMinutes, int deliveryMinutes, int maxActiveOrders, int issuePendingMinutes, int issueProcessingMinutes) {
}
