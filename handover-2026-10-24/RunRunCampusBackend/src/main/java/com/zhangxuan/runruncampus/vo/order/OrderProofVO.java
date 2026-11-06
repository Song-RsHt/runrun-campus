package com.zhangxuan.runruncampus.vo.order;

import com.zhangxuan.runruncampus.enums.order.OrderProofType;

import java.time.Instant;

/** 订单私有图片凭证响应；保持现有 JSON 字段及序列化契约。 */
public record OrderProofVO(
        Long id,
        OrderProofType type,
        String originalFileName,
        String contentType,
        long sizeBytes,
        Instant createdAt) {}
