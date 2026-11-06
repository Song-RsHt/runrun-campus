package com.zhangxuan.runruncampus.model;

/** 应用支付参数；仅承载内部协作所需的数据或校验逻辑。 */
public record AppPayParameters(
        String appId,
        String partnerId,
        String prepayId,
        String packageValue,
        String nonceStr,
        String timestamp,
        String sign) {}
