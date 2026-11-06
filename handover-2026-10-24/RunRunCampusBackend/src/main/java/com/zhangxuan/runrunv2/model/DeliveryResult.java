package com.zhangxuan.runrunv2.model;

/** 订阅通知投递结果；仅承载内部协作所需的数据或校验逻辑。 */
public record DeliveryResult(boolean sent, String error) {}
