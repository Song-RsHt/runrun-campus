package com.zhangxuan.runruncampus.vo.api;

/** 安卓版本响应；保持现有 JSON 字段及序列化契约。 */
public record AndroidVersion(
        boolean available, int latestVersionCode, String latestVersionName, String downloadUrl) {}
