package com.zhangxuan.runrunv2.model;

/** 订单取送时间；仅承载内部协作所需的数据或校验逻辑。 */
public record OrderSchedule(String timezone, String open, String close) {}
