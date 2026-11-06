package com.zhangxuan.runruncampus.vo;

import java.util.List;

/** 推送投递分页响应；保持现有 JSON 字段及序列化契约。 */
public record PushDeliveryPageVO(
        List<PushDeliveryItemVO> items, int page, int size, long totalElements, int totalPages) {}
