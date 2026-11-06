package com.zhangxuan.runruncampus.vo.admin;

/** 操作结果响应；保持现有 JSON 字段及序列化契约。 */
public record ActionResult(Long orderId, String action) {}
