package com.zhangxuan.runrunv2.vo;

/** 服务选项响应；保持现有 JSON 字段及序列化契约。 */
public record ServiceOptionVO(String code, String name, long baseCents, boolean zoneSurcharge) {}
