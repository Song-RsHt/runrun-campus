package com.zhangxuan.runruncampus.dto.request.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CreateRatingRequest(
    @Min(value = 1, message = "评分不能低于1星") @Max(value = 5, message = "评分不能高于5星") int score,
    @Size(max = 300, message = "评价不能超过300字") String comment) {}
