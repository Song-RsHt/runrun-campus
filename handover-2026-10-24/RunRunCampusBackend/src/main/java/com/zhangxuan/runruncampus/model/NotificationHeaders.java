package com.zhangxuan.runruncampus.model;

/** 支付通知验签元数据；仅承载内部协作所需的数据或校验逻辑。 */
public record NotificationHeaders(
        String serial, String timestamp, String nonce, String signature, String signType) {}
