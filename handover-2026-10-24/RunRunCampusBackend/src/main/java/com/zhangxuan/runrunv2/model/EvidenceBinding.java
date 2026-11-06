package com.zhangxuan.runrunv2.model;

import com.zhangxuan.runrunv2.enums.Kind;

/** 证据绑定；仅承载内部协作所需的数据或校验逻辑。 */
public record EvidenceBinding(String orderId, String taskId, String actorId, Kind kind) {}
