package com.zhangxuan.runrunv2.dto.request.fulfillment;

import com.zhangxuan.runrunv2.enums.fulfillment.Kind;

public record Upload(Long expectedVersion, Kind kind, String contentType, Long sizeBytes) {}
