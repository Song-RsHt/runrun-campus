package com.zhangxuan.runrunv2.model.fulfillment;

import java.time.OffsetDateTime;

/** 定位事件数据库投影；响应中的订单版本仍取调用方已锁定的聚合版本。 */
public record LocationEventRow(String id, OffsetDateTime receivedAt, boolean accepted) {}
