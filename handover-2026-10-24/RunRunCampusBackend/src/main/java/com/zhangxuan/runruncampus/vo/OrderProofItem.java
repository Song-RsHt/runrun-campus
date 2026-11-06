package com.zhangxuan.runruncampus.vo;

import com.zhangxuan.runruncampus.enums.OrderProofType;

import java.time.Instant;

/** 订单私有图片凭证条目响应；保持现有 JSON 字段及序列化契约。 */
public record OrderProofItem(
        Long id,
        OrderProofType type,
        String originalFileName,
        String contentType,
        long sizeBytes,
        UserSummary uploader,
        Instant createdAt) {}
