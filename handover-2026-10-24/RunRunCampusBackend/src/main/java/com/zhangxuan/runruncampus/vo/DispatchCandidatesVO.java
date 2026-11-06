package com.zhangxuan.runruncampus.vo;

import java.time.Instant;
import java.util.List;































public record DispatchCandidatesVO(long orderId, String orderNo, Instant generatedAt, List<DispatchCandidateVO> items, int page, int size, long totalElements) {
}
