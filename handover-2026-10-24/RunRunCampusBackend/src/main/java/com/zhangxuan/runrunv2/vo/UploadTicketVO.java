package com.zhangxuan.runrunv2.vo;

import java.time.OffsetDateTime;






public record UploadTicketVO(String uploadTicket, String uploadUrl, OffsetDateTime expiresAt) {
}
