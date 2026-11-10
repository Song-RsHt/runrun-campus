package com.zhangxuan.runruncampus.model.admin;

/** 订单创建与状态管理导出；仅承载内部协作所需的数据或校验逻辑。 */
public record OrderExport(byte[] content, boolean truncated) {}
