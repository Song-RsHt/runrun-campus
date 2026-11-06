package com.zhangxuan.runruncampus.model;



public record Settings(String businessHours, int waitMinutes, int deliveryMinutes, int maxActiveOrders, int issuePendingMinutes, int issueProcessingMinutes) {
}
