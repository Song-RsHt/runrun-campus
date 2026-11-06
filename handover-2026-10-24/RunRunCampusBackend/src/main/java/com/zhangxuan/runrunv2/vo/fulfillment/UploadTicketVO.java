package com.zhangxuan.runrunv2.vo.fulfillment;

import java.time.OffsetDateTime;

/** 上传票据响应；保持现有 JSON 字段及序列化契约。 */
public record UploadTicketVO(String uploadTicket, String uploadUrl, OffsetDateTime expiresAt) {}
