package com.zhangxuan.runrunv2.vo;

import com.zhangxuan.runrunv2.enums.Kind;
import java.time.OffsetDateTime;
import java.util.List;

public record UploadTicketVO(String uploadTicket, String uploadUrl, OffsetDateTime expiresAt) {
}
