package com.zhangxuan.runruncampus.vo;

import java.util.List;

/** 指挥台骑手队列响应；保持现有 JSON 字段及序列化契约。 */
public record CommandCenterCouriersVO(
        long online, long idle, List<CourierItem> items, int page, int size) {}
