package com.zhangxuan.runrunv2.dto.fulfillment;

import com.zhangxuan.runrunv2.enums.fulfillment.Kind;

/** 订单履约上传请求参数；保留入口的字段、构造器及校验契约。 */
public record FulfillmentUploadRequest(
        Long expectedVersion, Kind kind, String contentType, Long sizeBytes) {}
