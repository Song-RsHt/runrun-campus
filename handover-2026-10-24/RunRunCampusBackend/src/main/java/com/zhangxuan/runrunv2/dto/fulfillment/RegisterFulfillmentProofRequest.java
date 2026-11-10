package com.zhangxuan.runrunv2.dto.fulfillment;

import com.zhangxuan.runrunv2.enums.fulfillment.Kind;

/** 登记履约凭证请求参数；保留入口的字段、构造器及校验契约。 */
public record RegisterFulfillmentProofRequest(
        Long expectedVersion, Kind kind, String taskId, String uploadTicket) {}
