package com.zhangxuan.runruncampus.model;

public record AppPayParameters(String appId, String partnerId, String prepayId, String packageValue, String nonceStr, String timestamp, String sign) {
}
