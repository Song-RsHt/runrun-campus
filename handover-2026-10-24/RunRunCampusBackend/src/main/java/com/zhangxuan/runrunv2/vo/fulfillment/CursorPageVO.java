package com.zhangxuan.runrunv2.vo.fulfillment;

import java.util.List;

/** 游标分页响应；保持现有 JSON 字段及序列化契约。 */
public record CursorPageVO<T>(List<T> items, String nextCursor) {}
