package com.zhangxuan.runruncampus.vo.order;

import java.util.List;

/** 未读聊天汇总响应；保持现有 JSON 字段及序列化契约。 */
public record UnreadChatSummaryVO(long total, List<OrderUnreadVO> orders) {}
