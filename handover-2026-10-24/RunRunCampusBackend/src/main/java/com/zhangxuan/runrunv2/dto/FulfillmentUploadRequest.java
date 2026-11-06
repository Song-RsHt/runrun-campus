package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.enums.Kind;

public record FulfillmentUploadRequest(Long expectedVersion, Kind kind, String contentType, Long sizeBytes) {
}
