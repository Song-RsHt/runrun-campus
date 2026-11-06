package com.zhangxuan.runruncampus.dto.response.api.appversion;

public record AndroidVersion(
    boolean available, int latestVersionCode, String latestVersionName, String downloadUrl) {}
