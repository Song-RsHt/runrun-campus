package com.zhangxuan.runruncampus.model;



public record NotificationHeaders(String serial, String timestamp, String nonce, String signature, String signType) {
}
