package com.zhangxuan.runruncampus.dto;

import jakarta.validation.constraints.NotNull;

public record DispatchRequest(@NotNull Long courierId) {
}
