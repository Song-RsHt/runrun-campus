package com.zhangxuan.runrunv2.dto;

/** 投递区域变更请求参数；保留入口的字段、构造器及校验契约。 */
public record DeliveryZoneChangeRequest(
        String code, String name, Long packageSurchargeCents, Boolean enabled) {}
