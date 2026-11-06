package com.zhangxuan.runrunv2.dto;

import com.zhangxuan.runrunv2.enums.Kind;




public record Upload(Long expectedVersion, Kind kind, String contentType, Long sizeBytes) {
}
