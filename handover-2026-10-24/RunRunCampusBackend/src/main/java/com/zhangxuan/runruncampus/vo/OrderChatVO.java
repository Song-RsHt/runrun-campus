package com.zhangxuan.runruncampus.vo;

import java.time.Instant;

















public record OrderChatVO(Long id, Long senderId, String senderName, Long recipientId, String content, Instant createdAt) {
}
