package com.zhangxuan.runrunv2.vo.fulfillment;

import com.zhangxuan.runrunv2.enums.fulfillment.Kind;

/** 订单私有图片凭证响应；保持现有 JSON 字段及序列化契约。 */
public record OrderProofVO(
        String id,
        String orderId,
        String taskId,
        Kind kind,
        String storageStatus,
        long orderVersion) {}
