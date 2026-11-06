package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.model.PushDeliveryPage;




public record PushDeliveryOverview(long dead, long pending, long processing, PushDeliveryPage deliveries) {
}
