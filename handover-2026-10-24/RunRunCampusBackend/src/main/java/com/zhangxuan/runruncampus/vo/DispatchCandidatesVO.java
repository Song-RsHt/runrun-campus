package com.zhangxuan.runruncampus.vo;

import java.time.Instant;
import java.util.List;

/** 派单候选集响应；保持现有 JSON 字段及序列化契约。 */
public record DispatchCandidatesVO(
        long orderId,
        String orderNo,
        Instant generatedAt,
        List<DispatchCandidateVO> items,
        int page,
        int size,
        long totalElements) {}
