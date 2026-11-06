package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;






public record FulfillmentResultVO(FulfillmentOrderVO order, FulfillmentTaskVO task, OffsetDateTime confirmDeadline) {
}
