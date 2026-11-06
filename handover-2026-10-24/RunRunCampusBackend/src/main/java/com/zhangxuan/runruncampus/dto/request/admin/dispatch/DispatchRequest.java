package com.zhangxuan.runruncampus.dto.request.admin.dispatch;

import jakarta.validation.constraints.NotNull;

public record DispatchRequest(@NotNull Long courierId) {}
